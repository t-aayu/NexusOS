package com.nexus.prm.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexus.prm.NexusApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AutoBackupSettings(
  val enabled: Boolean = false,
  val destinationTreeUri: String? = null,
  val destinationDisplayName: String? = null,
  val password: String = "",
  val lastSuccessEpochMs: Long? = null,
  val lastFailureMessage: String? = null,
  val retentionCount: Int = 7,
)

data class ScheduledBackupFile(
  val name: String,
)

interface BackupDestination {
  fun isAccessible(): Boolean
  fun listScheduledFiles(): List<ScheduledBackupFile>
  fun writeAndReadBack(fileName: String, bytes: ByteArray): ByteArray
  fun deleteFile(fileName: String): Boolean
}

class SafBackupDestination(
  private val context: Context,
  private val treeUri: Uri,
) : BackupDestination {

  private fun hasPersistedPermission(): Boolean =
    context.contentResolver.persistedUriPermissions.any {
      it.uri == treeUri && it.isReadPermission && it.isWritePermission
    }

  private fun rootDir(): DocumentFile? {
    if (!hasPersistedPermission()) return null
    val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    return if (doc.exists() && doc.isDirectory && doc.canWrite()) doc else null
  }

  override fun isAccessible(): Boolean = rootDir() != null

  override fun listScheduledFiles(): List<ScheduledBackupFile> {
    val dir = rootDir() ?: return emptyList()
    return dir.listFiles()
      .filter { it.isFile && AutoBackupRunner.isScheduledBackupFileName(it.name.orEmpty()) }
      .mapNotNull { it.name?.let(::ScheduledBackupFile) }
  }

  override fun writeAndReadBack(fileName: String, bytes: ByteArray): ByteArray {
    val dir = rootDir() ?: error("Destination folder is not accessible")
    val created = dir.createFile("application/octet-stream", fileName)
      ?: error("Could not create backup file in selected destination")
    context.contentResolver.openOutputStream(created.uri)?.use { out ->
      out.write(bytes)
      out.flush()
    } ?: error("Could not write to backup file")

    return context.contentResolver.openInputStream(created.uri)?.use { it.readBytes() }
      ?: error("Could not read back written backup for verification")
  }

  override fun deleteFile(fileName: String): Boolean {
    val dir = rootDir() ?: return false
    val target = dir.findFile(fileName) ?: return false
    return target.delete()
  }
}

class AutoBackupRunner {
  private val runMutex = Mutex()

  suspend fun execute(
    repository: DirectoryRepository,
    destination: BackupDestination,
    password: String,
    retentionCount: Int = DEFAULT_RETENTION_COUNT,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): Result<String> = runMutex.withLock {
    runCatching {
      require(password.isNotEmpty()) { "Backup password is required for automatic backups." }
      require(destination.isAccessible()) {
        "Destination access is unavailable. Please re-select the backup folder."
      }

      val encrypted = repository.exportBackup(password)
      val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(nowEpochMs))
      val fileName = "$AUTO_FILE_PREFIX$stamp$AUTO_FILE_SUFFIX"

      val readBack = destination.writeAndReadBack(fileName, encrypted)
      // Verify the written file can be authenticated and parsed before pruning
      repository.inspectBackup(readBack, password)

      val scheduled = destination.listScheduledFiles()
        .sortedByDescending { it.name }
      if (scheduled.size > retentionCount) {
        for (old in scheduled.drop(retentionCount)) {
          destination.deleteFile(old.name)
        }
      }

      fileName
    }
  }

  companion object {
    const val DEFAULT_RETENTION_COUNT = 7
    const val AUTO_FILE_PREFIX = "nexus-auto-"
    const val AUTO_FILE_SUFFIX = ".nxbk"

    fun isScheduledBackupFileName(name: String): Boolean =
      name.startsWith(AUTO_FILE_PREFIX) && name.endsWith(AUTO_FILE_SUFFIX)
  }
}

class AutoBackupManager(
  private val context: Context,
  private val filesDir: File,
  private val crypto: LocalStorageCrypto,
  private val repository: DirectoryRepository,
) {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }
  private val settingsFile = File(filesDir, "nexus_auto_backup.enc")
  private val settingsMutex = Mutex()
  private val runner = AutoBackupRunner()

  private val _settings = MutableStateFlow(loadInitialSettings())
  val settings: StateFlow<AutoBackupSettings> = _settings.asStateFlow()

  private fun loadInitialSettings(): AutoBackupSettings {
    if (!settingsFile.exists()) return AutoBackupSettings()
    return runCatching {
      val decrypted = crypto.decrypt(settingsFile.readBytes())
      json.decodeFromString<AutoBackupSettings>(decrypted.decodeToString())
    }.getOrElse { AutoBackupSettings() }
  }

  fun hasDestinationAccess(): Boolean {
    val uriStr = _settings.value.destinationTreeUri ?: return false
    val uri = Uri.parse(uriStr)
    return SafBackupDestination(context, uri).isAccessible()
  }

  suspend fun configureSchedule(
    enabled: Boolean,
    destinationUri: Uri?,
    destinationDisplayName: String?,
    password: String?,
  ) = withContext(Dispatchers.IO) {
    settingsMutex.withLock {
      val current = _settings.value
      val updated = current.copy(
        enabled = enabled,
        destinationTreeUri = destinationUri?.toString() ?: current.destinationTreeUri,
        destinationDisplayName = destinationDisplayName ?: current.destinationDisplayName,
        password = if (!password.isNullOrEmpty()) password else current.password,
      )
      persistSettings(updated)
      _settings.value = updated
      syncWorkManager(updated.enabled)
    }
  }

  suspend fun runBackupNow(): Result<String> = withContext(Dispatchers.IO) {
    val current = settingsMutex.withLock { _settings.value }
    val uriStr = current.destinationTreeUri
    if (uriStr.isNullOrBlank()) {
      val failure = "No backup destination folder selected."
      recordFailure(failure)
      return@withContext Result.failure(IllegalStateException(failure))
    }
    if (current.password.isEmpty()) {
      val failure = "No automatic backup password configured."
      recordFailure(failure)
      return@withContext Result.failure(IllegalStateException(failure))
    }

    val destination = SafBackupDestination(context, Uri.parse(uriStr))
    val result = runner.execute(
      repository = repository,
      destination = destination,
      password = current.password,
      retentionCount = current.retentionCount,
    )

    result.onSuccess {
      settingsMutex.withLock {
        val updated = _settings.value.copy(
          lastSuccessEpochMs = System.currentTimeMillis(),
          lastFailureMessage = null,
        )
        persistSettings(updated)
        _settings.value = updated
      }
    }.onFailure { err ->
      recordFailure(err.message ?: "Automatic backup failed.")
    }

    result
  }

  private suspend fun recordFailure(message: String) {
    settingsMutex.withLock {
      val updated = _settings.value.copy(lastFailureMessage = message)
      persistSettings(updated)
      _settings.value = updated
    }
  }

  private fun persistSettings(settings: AutoBackupSettings) {
    if (!filesDir.exists()) filesDir.mkdirs()
    val encrypted = crypto.encrypt(json.encodeToString(settings).encodeToByteArray())
    val tmp = File(filesDir, "nexus_auto_backup.enc.tmp")
    tmp.writeBytes(encrypted)
    if (!tmp.renameTo(settingsFile)) {
      settingsFile.writeBytes(encrypted)
      tmp.delete()
    }
  }

  private fun syncWorkManager(enabled: Boolean) {
    val wm = WorkManager.getInstance(context)
    if (enabled) {
      val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
      wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    } else {
      wm.cancelUniqueWork(WORK_NAME)
    }
  }

  companion object {
    private const val WORK_NAME = "nexus_daily_auto_backup"
  }
}

class AutoBackupWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val app = applicationContext as NexusApp
    val settings = app.autoBackupManager.settings.value
    if (!settings.enabled) return Result.success()
    val outcome = app.autoBackupManager.runBackupNow()
    return if (outcome.isSuccess) Result.success() else Result.retry()
  }
}
