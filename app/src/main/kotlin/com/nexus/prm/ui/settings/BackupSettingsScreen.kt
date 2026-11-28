package com.nexus.prm.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.prm.data.AutoBackupManager
import com.nexus.prm.data.BackupDestinationStatus
import com.nexus.prm.data.BackupLimits
import com.nexus.prm.data.BackupPreview
import com.nexus.prm.data.CsvExporter
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.FixedBackupLocation
import com.nexus.prm.data.MergePreview
import com.nexus.prm.data.RestoreMerger
import com.nexus.prm.data.RestoreMode
import com.nexus.prm.data.SafBackupDestination
import com.nexus.prm.ui.theme.NexusFrostedStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class BackupDocumentSession : ViewModel() {
  var exportPassword: String? = null
}

private enum class FullReplaceConfirmationStep {
  NONE,
  FIRST,
  SECOND,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BackupSettingsScreen(
  repository: DirectoryRepository,
  autoBackupManager: AutoBackupManager,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val profiles by repository.profiles.collectAsStateWithLifecycle()
  val autoBackupSettings by autoBackupManager.settings.collectAsStateWithLifecycle()

  var isWorking by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  var showCreatePasswordDialog by remember { mutableStateOf(false) }
  val documentSession: BackupDocumentSession = viewModel { BackupDocumentSession() }

  var enableAfterPassword by remember { mutableStateOf<Boolean?>(null) }
  var includeMeetingNotesInCsv by remember { mutableStateOf(false) }

  var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
  var restorePasswordError by remember { mutableStateOf<String?>(null) }
  var restorePreview by remember { mutableStateOf<BackupPreview?>(null) }
  var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
  var keepDuplicateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var replaceConfirmStep by remember { mutableStateOf(FullReplaceConfirmationStep.NONE) }

  var folderStatusRefresh by remember { mutableStateOf(0) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { folderStatusRefresh++ }
  val destinationStatus by produceState<BackupDestinationStatus?>(
    null,
    autoBackupSettings.destinationTreeUri,
    folderStatusRefresh,
  ) {
    value = withContext(Dispatchers.IO) { autoBackupManager.destinationStatus() }
  }

  fun runBackupAction(fallbackError: String, action: suspend () -> String?) {
    if (isWorking) return
    isWorking = true
    errorMessage = null
    statusMessage = null
    scope.launch {
      try {
        statusMessage = action()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (err: Exception) {
        errorMessage = err.message?.takeIf { it.isNotBlank() } ?: fallbackError
      } finally {
        isWorking = false
      }
    }
  }

  fun clearRestoreFlow(message: String? = null) {
    restorePreview = null
    restoreMode = RestoreMode.MERGE
    keepDuplicateIds = emptySet()
    replaceConfirmStep = FullReplaceConfirmationStep.NONE
    if (message != null) statusMessage = message
  }

  val createBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
  ) { uri: Uri? ->
    val password = documentSession.exportPassword
    documentSession.exportPassword = null
    if (uri == null) {
      statusMessage = "Backup cancelled."
      return@rememberLauncherForActivityResult
    }
    if (password == null) {
      errorMessage = "Enter a backup password again to create this backup."
      return@rememberLauncherForActivityResult
    }
    isWorking = true
    errorMessage = null
    statusMessage = null
    scope.launch {
      runCatching {
        val encrypted = repository.exportBackup(password)
        val readBack = withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(encrypted)
            out.flush()
          } ?: error("Could not open destination file")
          context.contentResolver.openInputStream(uri)?.use {
            BackupLimits.readEncryptedBackupBounded(it)
          } ?: error("Could not reopen saved backup for verification")
        }
        repository.inspectBackup(readBack, password)
      }.onSuccess { preview ->
        statusMessage = "Encrypted backup saved and verified (${preview.profiles.size} profiles)."
      }.onFailure { err ->
        errorMessage = err.message ?: "Failed to create backup."
      }
      isWorking = false
    }
  }

  val openBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri == null) {
      statusMessage = "Restore cancelled."
      return@rememberLauncherForActivityResult
    }
    isWorking = true
    errorMessage = null
    statusMessage = null
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use {
            BackupLimits.readEncryptedBackupBounded(it)
          } ?: error("Could not read selected file")
        }
      }.onSuccess { bytes ->
        restorePasswordError = null
        pendingRestoreBytes = bytes
      }.onFailure { err ->
        errorMessage = err.message ?: "Could not open backup file."
      }
      isWorking = false
    }
  }

  var showSetupBackupFolderGuide by remember { mutableStateOf(false) }

  val autoBackupTreeLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    val selectionKind = FixedBackupLocation.classifyTreeUri(uri)
    if (selectionKind == FixedBackupLocation.SelectionKind.OTHER) {
      statusMessage = null
      errorMessage = "Please create or select ${FixedBackupLocation.FULL_DISPLAY_PATH} (or ${FixedBackupLocation.PARENT_FOLDER_NAME})."
      return@rememberLauncherForActivityResult
    }
    runBackupAction("Could not set up the backup folder.") {
      val accessible = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        SafBackupDestination(context, uri).isAccessible()
      }
      check(accessible) {
        "Could not access ${FixedBackupLocation.FULL_DISPLAY_PATH}. Select the folder again to grant access."
      }
      autoBackupManager.configureSchedule(
        enabled = autoBackupSettings.enabled,
        destinationUri = uri,
        destinationDisplayName = FixedBackupLocation.RELATIVE_PATH,
        password = null,
      )
      folderStatusRefresh++
      "Backup folder ready: ${FixedBackupLocation.RELATIVE_PATH}."
    }
  }

  val exportCsvLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/csv"),
  ) { uri: Uri? ->
    if (uri == null) {
      statusMessage = "CSV export cancelled."
      return@rememberLauncherForActivityResult
    }
    isWorking = true
    errorMessage = null
    statusMessage = null
    scope.launch {
      runCatching {
        val csvContent = CsvExporter.export(profiles, includeMeetingNotes = includeMeetingNotesInCsv)
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(csvContent.toByteArray(Charsets.UTF_8))
            out.flush()
          } ?: error("Could not open CSV destination")
        }
      }.onSuccess {
        statusMessage = "Exported ${profiles.size} profiles to CSV."
      }.onFailure { err ->
        errorMessage = err.message ?: "CSV export failed."
      }
      isWorking = false
    }
  }

  SettingsPage(
    title = "Backups",
    onBack = onBack,
    modifier = modifier,
    busy = isWorking,
    statusMessage = statusMessage,
    errorMessage = errorMessage,
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      border = NexusFrostedStyle.borderStroke(MaterialTheme.colorScheme),
      colors = NexusFrostedStyle.cardColors(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Backup & Restore",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "A password-encrypted backup includes your ${profiles.size} profiles, photos, posting history, introductions, groups, and contact card.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "On a new installation, set up Appearance, Caller ID, and automatic backups separately after restoring your directory.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Button(
            onClick = {
              errorMessage = null
              statusMessage = null
              showCreatePasswordDialog = true
            },
            enabled = !isWorking,
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create backup")
          }
          OutlinedButton(
            onClick = {
              errorMessage = null
              statusMessage = null
              openBackupLauncher.launch(arrayOf("*/*"))
            },
            enabled = !isWorking,
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Restore backup")
          }
        }
      }
  }

  // Automatic encrypted backups
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    border = NexusFrostedStyle.borderStroke(MaterialTheme.colorScheme),
    colors = NexusFrostedStyle.cardColors(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Automatic daily backups",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Keeps the latest 7 verified automatic backups.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = autoBackupSettings.enabled,
          enabled = !isWorking,
          onCheckedChange = { wantEnable ->
            if (wantEnable && autoBackupSettings.password.isEmpty()) {
              enableAfterPassword = true
            } else {
              runBackupAction("Could not change the daily schedule.") {
                autoBackupManager.configureSchedule(wantEnable, null, null, null)
                if (wantEnable) "Daily backups enabled." else "Daily backups turned off."
              }
            }
          },
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = FixedBackupLocation.RELATIVE_PATH,
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          text = when (destinationStatus) {
            null -> "Checking folder access"
            BackupDestinationStatus.READY -> "Folder ready"
            BackupDestinationStatus.SETUP_REQUIRED -> "Select a backup folder"
            BackupDestinationStatus.LEGACY_OTHER_FOLDER -> "Select the current backup folder"
            BackupDestinationStatus.ACCESS_REQUIRED -> "Folder access required"
          },
          style = MaterialTheme.typography.labelLarge,
          color = when (destinationStatus) {
            BackupDestinationStatus.READY -> MaterialTheme.colorScheme.primary
            BackupDestinationStatus.ACCESS_REQUIRED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }
      if (destinationStatus == BackupDestinationStatus.LEGACY_OTHER_FOLDER) {
        Text(
          text = "This installation used a different backup folder. Select ${FixedBackupLocation.FULL_DISPLAY_PATH} for daily backups.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          fontWeight = FontWeight.Medium,
        )
      } else if (destinationStatus == BackupDestinationStatus.ACCESS_REQUIRED) {
        Text(
          text = "Access to ${FixedBackupLocation.FULL_DISPLAY_PATH} was lost. Set up the backup folder again to grant access.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          fontWeight = FontWeight.Medium,
        )
      }

      val lastVerified = remember(autoBackupSettings.lastSuccessEpochMs) {
        autoBackupSettings.lastSuccessEpochMs?.let { timestamp ->
          java.text.DateFormat.getDateTimeInstance().format(Date(timestamp))
        }
      }
      Text(
        text = if (lastVerified == null) "No automatic backup verified yet"
          else "Last verified automatic backup: $lastVerified",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      autoBackupSettings.lastFailureMessage?.let { failure ->
        Text(
          text = "Latest failure: $failure",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = { showSetupBackupFolderGuide = true },
          enabled = !isWorking,
          modifier = Modifier.weight(1f),
        ) {
          Text(if (destinationStatus == BackupDestinationStatus.ACCESS_REQUIRED) "Restore folder access" else "Set up backup folder")
        }
        OutlinedButton(
          onClick = { enableAfterPassword = autoBackupSettings.enabled },
          enabled = !isWorking,
          modifier = Modifier.weight(1f),
        ) {
          Text(if (autoBackupSettings.password.isEmpty()) "Set password" else "Change password")
        }
      }
      Button(
        onClick = {
          runBackupAction("Automatic backup failed.") {
            try {
              val fileName = autoBackupManager.runBackupNow().getOrThrow()
              "Automatic backup verified: $fileName"
            } finally {
              folderStatusRefresh++
            }
          }
        },
        enabled = !isWorking &&
          destinationStatus == BackupDestinationStatus.READY &&
          autoBackupSettings.password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Run backup now")
      }
      if (showSetupBackupFolderGuide) {
        AlertDialog(
          onDismissRequest = { showSetupBackupFolderGuide = false },
          title = { Text("Set up backup folder") },
          text = {
            Text(
              "In the folder picker, create or open ${FixedBackupLocation.FULL_DISPLAY_PATH} " +
                "(or select ${FixedBackupLocation.PARENT_FOLDER_NAME} and NexusOS will create its " +
                "${FixedBackupLocation.BACKUP_FOLDER_NAME} subfolder), then tap Use this folder.",
            )
          },
          confirmButton = {
            Button(
              onClick = {
                showSetupBackupFolderGuide = false
                autoBackupTreeLauncher.launch(FixedBackupLocation.initialPickerUri())
              },
            ) {
              Text("Continue")
            }
          },
          dismissButton = {
            TextButton(onClick = { showSetupBackupFolderGuide = false }) {
              Text("Cancel")
            }
          },
        )
      }
    }
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    border = NexusFrostedStyle.borderStroke(MaterialTheme.colorScheme),
    colors = NexusFrostedStyle.cardColors(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Export CSV",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "Export a readable directory snapshot in UTF-8. Use an encrypted backup to transfer photos, posting history, and introductions.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { includeMeetingNotesInCsv = !includeMeetingNotesInCsv },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = includeMeetingNotesInCsv,
          onCheckedChange = { includeMeetingNotesInCsv = it },
        )
        Text("Include meeting notes")
      }
      OutlinedButton(
        onClick = {
          val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
          exportCsvLauncher.launch("nexus-directory-$dateStamp.csv")
        },
        enabled = !isWorking,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Export CSV")
      }
    }
  }
  }

  enableAfterPassword?.let { enable ->
    CreateBackupPasswordDialog(
      onDismiss = { enableAfterPassword = null },
      onConfirm = { password ->
        enableAfterPassword = null
        runBackupAction("Could not save the automatic backup password.") {
          autoBackupManager.configureSchedule(enable, null, null, password)
          if (enable) "Password saved. Daily backups are enabled." else "Password saved."
        }
      },
    )
  }

  if (showCreatePasswordDialog) {
    CreateBackupPasswordDialog(
      onDismiss = { showCreatePasswordDialog = false },
      onConfirm = { password ->
        showCreatePasswordDialog = false
        documentSession.exportPassword = password
        val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        createBackupLauncher.launch("nexus-backup-$dateStamp.nxbk")
      },
    )
  }

  pendingRestoreBytes?.let { bytes ->
    RestorePasswordDialog(
      busy = isWorking,
      verificationError = restorePasswordError,
      onDismiss = {
        pendingRestoreBytes = null
        restorePasswordError = null
        statusMessage = "Restore cancelled."
      },
      onConfirm = { password ->
        if (!isWorking) {
          isWorking = true
          restorePasswordError = null
          scope.launch {
            try {
              val preview = repository.inspectBackup(bytes, password)
              pendingRestoreBytes = null
              restoreMode = RestoreMode.MERGE
              keepDuplicateIds = emptySet()
              replaceConfirmStep = FullReplaceConfirmationStep.NONE
              restorePreview = preview
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: AEADBadTagException) {
              restorePasswordError = "Incorrect password or damaged backup. Try the password again."
            } catch (err: Exception) {
              pendingRestoreBytes = null
              errorMessage = err.message ?: "Invalid or damaged backup."
            } finally {
              isWorking = false
            }
          }
        }
      },
    )
  }

  restorePreview?.let { preview ->
    val mergePreview = remember(profiles, preview) {
      RestoreMerger.buildPreview(profiles, preview)
    }

    if (replaceConfirmStep == FullReplaceConfirmationStep.NONE) {
      RestorePreviewDialog(
        backupPreview = preview,
        mergePreview = mergePreview,
        localCount = profiles.size,
        mode = restoreMode,
        keepDuplicateIds = keepDuplicateIds,
        onModeSelected = { restoreMode = it },
        onToggleDuplicateKeep = { profileId, keep ->
          keepDuplicateIds = if (keep) keepDuplicateIds + profileId else keepDuplicateIds - profileId
        },
        onDismiss = { clearRestoreFlow("Restore cancelled.") },
        onConfirm = {
          if (restoreMode == RestoreMode.MERGE) {
            val activePreview = preview
            val keptIds = keepDuplicateIds
            clearRestoreFlow()
            isWorking = true
            errorMessage = null
            scope.launch {
              runCatching {
                repository.applyMergeRestore(activePreview, keptIds)
              }.onSuccess { outcome ->
                statusMessage = "Merge complete: ${outcome.mergedProfiles.size} total profiles in directory."
              }.onFailure { err ->
                errorMessage = (err.message ?: "Merge restore failed.") + " Existing data was not changed."
              }
              isWorking = false
            }
          } else {
            replaceConfirmStep = FullReplaceConfirmationStep.FIRST
          }
        },
      )
    }

    if (replaceConfirmStep == FullReplaceConfirmationStep.FIRST) {
      AlertDialog(
        onDismissRequest = { clearRestoreFlow("Full replacement cancelled.") },
        title = { Text("Full replacement (1 of 2)") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Current directory: ${profiles.size} profiles")
            Text("Backup archive: ${preview.profiles.size} profiles")
            Text(
              "Full replacement will remove all current profiles, photos, tags, reusable groups, memberships, and your contact card, and replace them with this backup.",
              color = MaterialTheme.colorScheme.error,
            )
          }
        },
        confirmButton = {
          Button(onClick = { replaceConfirmStep = FullReplaceConfirmationStep.SECOND }) {
            Text("Continue")
          }
        },
        dismissButton = {
          TextButton(onClick = { clearRestoreFlow("Full replacement cancelled.") }) {
            Text("Cancel")
          }
        },
      )
    }

    if (replaceConfirmStep == FullReplaceConfirmationStep.SECOND) {
      AlertDialog(
        onDismissRequest = { clearRestoreFlow("Full replacement cancelled.") },
        title = { Text("Confirm full replacement (2 of 2)") },
        text = {
          Text(
            "This backup will permanently replace your current profiles, photos, tags, reusable groups, memberships, and owner contact card. This cannot be undone.",
          )
        },
        confirmButton = {
          Button(
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
            onClick = {
              val activePreview = preview
              clearRestoreFlow()
              isWorking = true
              errorMessage = null
              scope.launch {
                runCatching {
                  repository.applyBackup(activePreview)
                }.onSuccess {
                  statusMessage = "Directory replaced with ${activePreview.profiles.size} profiles from backup."
                }.onFailure { err ->
                  errorMessage = (err.message ?: "Full replacement failed.") + " Existing data was not changed."
                }
                isWorking = false
              }
            },
          ) {
            Text("Replace directory")
          }
        },
        dismissButton = {
          TextButton(onClick = { clearRestoreFlow("Full replacement cancelled.") }) {
            Text("Cancel")
          }
        },
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestorePreviewDialog(
  backupPreview: BackupPreview,
  mergePreview: MergePreview,
  localCount: Int,
  mode: RestoreMode,
  keepDuplicateIds: Set<String>,
  onModeSelected: (RestoreMode) -> Unit,
  onToggleDuplicateKeep: (String, Boolean) -> Unit,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  val photoCount = backupPreview.profiles.count { it.photoFileName != null }
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      tonalElevation = 4.dp,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Restore backup",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Verified ${backupPreview.profiles.size} profiles ($photoCount with photos) and ${backupPreview.groups.size} groups.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onModeSelected(RestoreMode.MERGE) },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = mode == RestoreMode.MERGE,
              onClick = { onModeSelected(RestoreMode.MERGE) },
            )
            Column {
              Text("Merge with current directory", fontWeight = FontWeight.SemiBold)
              Text(
                "Keep local profiles and add missing backup data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onModeSelected(RestoreMode.FULL_REPLACEMENT) },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = mode == RestoreMode.FULL_REPLACEMENT,
              onClick = { onModeSelected(RestoreMode.FULL_REPLACEMENT) },
            )
            Column {
              Text("Full replacement", fontWeight = FontWeight.SemiBold)
              Text(
                "Replace all $localCount current profiles (requires 2 confirmations)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        HorizontalDivider()

        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (mode == RestoreMode.MERGE) {
            item {
              Text(
                text = "${mergePreview.toAdd.size} to add · ${mergePreview.matchedById.size} matched by ID · " +
                  "${mergePreview.localOnlyCount} local only · ${mergePreview.duplicates.size} possible duplicates",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            items(mergePreview.toAdd) { profile ->
              Text("Add ${profile.name}", style = MaterialTheme.typography.bodySmall)
            }
            items(mergePreview.matchedById) { (local, _) ->
              Text("Fill missing details for ${local.name}", style = MaterialTheme.typography.bodySmall)
            }
            if (mergePreview.duplicates.isNotEmpty()) {
              item {
                Text(
                  "Possible duplicates",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              items(mergePreview.duplicates) { duplicate ->
                val id = duplicate.incomingProfile.id
                val keep = id in keepDuplicateIds
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                  Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Text(
                      "${duplicate.incomingProfile.name} matches ${duplicate.matchedLocalProfile.name} (${duplicate.matchedField.label})",
                      style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      FilterChip(
                        selected = !keep,
                        onClick = { onToggleDuplicateKeep(id, false) },
                        label = { Text("Skip") },
                      )
                      FilterChip(
                        selected = keep,
                        onClick = { onToggleDuplicateKeep(id, true) },
                        label = { Text("Keep separately") },
                      )
                    }
                  }
                }
              }
            }
          } else {
            item {
              Text(
                text = "Full replacement will replace the current directory and contact card with this backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) { Text("Cancel") }
          Spacer(modifier = Modifier.width(8.dp))
          Button(onClick = onConfirm) {
            Text(if (mode == RestoreMode.MERGE) "Merge & Restore" else "Continue")
          }
        }
      }
    }
  }
}

@Composable
private fun CreateBackupPasswordDialog(
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var password by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Encrypt backup") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose a password to encrypt this backup. You will need it to restore on another phone.")
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            error = null
          },
          label = { Text("Password") },
          singleLine = true,
          visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
              Icon(
                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (showPassword) "Hide password" else "Show password",
              )
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = confirm,
          onValueChange = {
            confirm = it
            error = null
          },
          label = { Text("Confirm password") },
          singleLine = true,
          visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          isError = error != null,
          supportingText = error?.let { msg -> { Text(msg) } },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when {
            password.isEmpty() -> error = "Password is required"
            password != confirm -> error = "Passwords do not match"
            else -> onConfirm(password)
          }
        },
      ) {
        Text("Continue")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun RestorePasswordDialog(
  busy: Boolean,
  verificationError: String?,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("Enter backup password") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Enter the password used when this backup was created.")
        OutlinedTextField(
          value = password,
          enabled = !busy,
          onValueChange = {
            password = it
            error = null
          },
          label = { Text("Password") },
          singleLine = true,
          visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
              Icon(
                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (showPassword) "Hide password" else "Show password",
              )
            }
          },
          isError = error != null || verificationError != null,
          supportingText = (error ?: verificationError)?.let { msg -> { Text(msg) } },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        enabled = !busy,
        onClick = {
          if (password.isEmpty()) {
            error = "Password is required"
          } else {
            onConfirm(password)
          }
        },
      ) {
        Text(if (busy) "Verifying…" else "Verify")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
    },
  )
}
