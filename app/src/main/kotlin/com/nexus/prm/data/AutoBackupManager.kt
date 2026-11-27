package com.nexus.prm.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

enum class BackupDestinationStatus {
  READY,
  SETUP_REQUIRED,
  ACCESS_REQUIRED,
  LEGACY_OTHER_FOLDER,
}

object FixedBackupLocation {
  const val PARENT_FOLDER_NAME = "NexusOS"
  const val BACKUP_FOLDER_NAME = "Backup"
  const val RELATIVE_PATH = "NexusOS/Backup"
  const val FULL_DISPLAY_PATH = "Internal storage/NexusOS/Backup"

  enum class SelectionKind {
    BACKUP_FOLDER,
    PARENT_FOLDER,
    OTHER,
  }

  fun classifyDocumentId(documentId: String?): SelectionKind {
    if (documentId.isNullOrBlank()) return SelectionKind.OTHER
    val colonIndex = documentId.indexOf(':')
    if (colonIndex < 0) return SelectionKind.OTHER
    val volume = documentId.substring(0, colonIndex)
    if (!volume.equals("primary", ignoreCase = true)) return SelectionKind.OTHER
    val relative = documentId.substring(colonIndex + 1).trim('/')
    return when {
      relative.equals(RELATIVE_PATH, ignoreCase = true) -> SelectionKind.BACKUP_FOLDER
      relative.equals(PARENT_FOLDER_NAME, ignoreCase = true) -> SelectionKind.PARENT_FOLDER
      else -> SelectionKind.OTHER
    }
  }

  fun classifyTreeUri(treeUri: Uri): SelectionKind {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
      ?: treeUri.lastPathSegment
    return classifyDocumentId(docId)
  }

  fun initialPickerUri(): Uri? = runCatching {
    DocumentsContract.buildDocumentUri(
      "com.android.externalstorage.documents",
      "primary:$RELATIVE_PATH",
    )
  }.getOrNull()
}

interface BackupDestination {
  fun isAccessible(): Boolean
  fun listScheduledFiles(): List<ScheduledBackupFile>
  fun writeStagingAndReadBack(stagingName: String, bytes: ByteArray): ByteArray
  fun promoteStagingToFinal(stagingName: String, finalName: String): Boolean
  fun deleteFile(fileName: String): Boolean
}

class SafBackupDestination(
  private val context: Context,
  private val treeUri: Uri,
) : BackupDestination {

  private val stagedDocs = ConcurrentHashMap<String, DocumentFile>()

  private fun hasPersistedPermission(): Boolean =
    context.contentResolver.persistedUriPermissions.any {
      it.uri == treeUri && it.isReadPermission && it.isWritePermission
    }

  private fun rootDir(): DocumentFile? {
    if (!hasPersistedPermission()) return null
    val selectionKind = FixedBackupLocation.classifyTreeUri(treeUri)
    if (selectionKind == FixedBackupLocation.SelectionKind.OTHER) return null
    val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    if (!doc.exists() || !doc.isDirectory || !doc.canWrite()) return null

    return when (selectionKind) {
      FixedBackupLocation.SelectionKind.BACKUP_FOLDER -> doc
      FixedBackupLocation.SelectionKind.PARENT_FOLDER -> {
        val existing = doc.findFile(FixedBackupLocation.BACKUP_FOLDER_NAME)
        val backupDir = when {
          existing != null && existing.isDirectory -> existing
          existing == null -> doc.createDirectory(FixedBackupLocation.BACKUP_FOLDER_NAME)
          else -> null
        }
        if (backupDir != null && backupDir.exists() && backupDir.isDirectory && backupDir.canWrite()) {
          backupDir
        } else {
          null
        }
      }
      FixedBackupLocation.SelectionKind.OTHER -> null
    }
  }

  override fun isAccessible(): Boolean = rootDir() != null

  override fun listScheduledFiles(): List<ScheduledBackupFile> {
    val dir = rootDir() ?: return emptyList()
    return dir.listFiles()
      .filter { it.isFile && AutoBackupRunner.isScheduledBackupFileName(it.name.orEmpty()) }
      .mapNotNull { it.name?.let(::ScheduledBackupFile) }
  }

  override fun writeStagingAndReadBack(stagingName: String, bytes: ByteArray): ByteArray {
    val dir = rootDir() ?: error("Destination folder is not accessible")
    val created = dir.createFile("application/octet-stream", stagingName)
      ?: error("Could not create staging backup file in selected destination")
    stagedDocs[stagingName] = created
    return try {
      context.contentResolver.openOutputStream(created.uri)?.use { out ->
        out.write(bytes)
        out.flush()
      } ?: error("Could not write to staging backup file")

      context.contentResolver.openInputStream(created.uri)?.use { it.readBytes() }
        ?: error("Could not reopen staging backup file for verification")
    } catch (t: Throwable) {
      stagedDocs.remove(stagingName)
      runCatching { created.delete() }
      throw t
    }
  }

  override fun promoteStagingToFinal(stagingName: String, finalName: String): Boolean {
    val dir = rootDir() ?: return false
    val staged = stagedDocs.remove(stagingName) ?: dir.findFile(stagingName) ?: return false
    val renamed = runCatching { staged.renameTo(finalName) }.getOrDefault(false)
    if (!renamed) {
      runCatching { staged.delete() }
      return false
    }
    return dir.findFile(finalName)?.exists() == true
  }

  override fun deleteFile(fileName: String): Boolean {
    val staged = stagedDocs.remove(fileName)
    if (staged != null) {
      return runCatching { staged.delete() }.getOrDefault(false)
    }
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
    @Suppress("UNUSED_PARAMETER") stagingDir: File? = null,
  ): Result<String> = runMutex.withLock {
    runCatching {
      require(password.isNotEmpty()) { "Backup password is required for automatic backups." }
      require(destination.isAccessible()) {
        "Destination access is unavailable. Please re-select the backup folder."
      }

      val encrypted = repository.exportBackup(password)
      val stagingName = "$STAGING_FILE_PREFIX${UUID.randomUUID()}$STAGING_FILE_SUFFIX"
      val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(nowEpochMs))
      val fileName = "$AUTO_FILE_PREFIX$stamp$AUTO_FILE_SUFFIX"

      try {
        val destinationReadBack = destination.writeStagingAndReadBack(stagingName, encrypted)
        repository.inspectBackup(destinationReadBack, password)

        val promoted = destination.promoteStagingToFinal(stagingName, fileName)
        check(promoted) {
          "Destination provider could not publish verified backup as $fileName"
        }
      } catch (t: Throwable) {
        runCatching { destination.deleteFile(stagingName) }
        throw t
      }

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
    const val STAGING_FILE_PREFIX = ".nexus-staging-"
    const val STAGING_FILE_SUFFIX = ".tmp"

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

  fun destinationStatus(): BackupDestinationStatus {
    val uriStr = _settings.value.destinationTreeUri
    if (uriStr.isNullOrBlank()) return BackupDestinationStatus.SETUP_REQUIRED
    val uri = Uri.parse(uriStr)
    if (FixedBackupLocation.classifyTreeUri(uri) == FixedBackupLocation.SelectionKind.OTHER) {
      return BackupDestinationStatus.LEGACY_OTHER_FOLDER
    }
    return if (SafBackupDestination(context, uri).isAccessible()) {
      BackupDestinationStatus.READY
    } else {
      BackupDestinationStatus.ACCESS_REQUIRED
    }
  }

  fun hasDestinationAccess(): Boolean = destinationStatus() == BackupDestinationStatus.READY

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

    val uri = Uri.parse(uriStr)
    if (FixedBackupLocation.classifyTreeUri(uri) == FixedBackupLocation.SelectionKind.OTHER) {
      val failure = "Scheduled backups require ${FixedBackupLocation.FULL_DISPLAY_PATH}. Please set up the backup folder."
      recordFailure(failure)
      return@withContext Result.failure(IllegalStateException(failure))
    }

    val destination = SafBackupDestination(context, uri)
    val result = runner.execute(
      repository = repository,
      destination = destination,
      password = current.password,
      retentionCount = current.retentionCount,
      stagingDir = File(filesDir, "auto_backup_staging"),
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
