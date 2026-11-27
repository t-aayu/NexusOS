package com.nexus.prm.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.AutoBackupManager
import com.nexus.prm.data.BackupPreview
import com.nexus.prm.data.CsvExporter
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.MergePreview
import com.nexus.prm.data.RestoreMerger
import com.nexus.prm.data.RestoreMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FullReplaceConfirmationStep {
  NONE,
  FIRST,
  SECOND,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  repository: DirectoryRepository,
  autoBackupManager: AutoBackupManager,
  onBack: () -> Unit,
  onOpenOwnerCard: () -> Unit,
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
  var pendingExportPassword by remember { mutableStateOf<String?>(null) }

  var showAutoBackupPasswordDialog by remember { mutableStateOf(false) }
  var includeMeetingNotesInCsv by remember { mutableStateOf(false) }

  var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
  var showRestorePasswordDialog by remember { mutableStateOf(false) }
  var restorePreview by remember { mutableStateOf<BackupPreview?>(null) }
  var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
  var keepDuplicateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var replaceConfirmStep by remember { mutableStateOf(FullReplaceConfirmationStep.NONE) }

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
    val password = pendingExportPassword
    pendingExportPassword = null
    if (uri == null || password == null) {
      statusMessage = "Backup cancelled."
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
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not reopen saved backup for verification")
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
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read selected file")
        }
      }.onSuccess { bytes ->
        pendingRestoreBytes = bytes
        showRestorePasswordDialog = true
      }.onFailure { err ->
        errorMessage = err.message ?: "Could not open backup file."
      }
      isWorking = false
    }
  }

  val autoBackupTreeLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }
    val displayName = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "Selected folder"
    scope.launch {
      autoBackupManager.configureSchedule(
        enabled = autoBackupSettings.enabled,
        destinationUri = uri,
        destinationDisplayName = displayName,
        password = null,
      )
      statusMessage = "Automatic backup folder set to $displayName."
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

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (isWorking) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }

      statusMessage?.let { msg ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = msg,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }

      errorMessage?.let { msg ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = msg,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
        }
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "My contact card",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Choose which details to share and generate an offline vCard QR code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = onOpenOwnerCard,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Edit & share my contact card")
          }
        }
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
            text = "Create a password-encrypted backup containing your ${profiles.size} saved profiles, photos, favourites, tags, groups, and contact card for transfer to another phone.",
            style = MaterialTheme.typography.bodyMedium,
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
              onCheckedChange = { wantEnable ->
                if (!wantEnable) {
                  scope.launch {
                    autoBackupManager.configureSchedule(false, null, null, null)
                  }
                } else if (autoBackupSettings.password.isEmpty()) {
                  showAutoBackupPasswordDialog = true
                } else {
                  scope.launch {
                    autoBackupManager.configureSchedule(true, null, null, null)
                  }
                }
              },
            )
          }

          val hasAccess = remember(autoBackupSettings.destinationTreeUri) {
            autoBackupManager.hasDestinationAccess()
          }
          val folderLabel = autoBackupSettings.destinationDisplayName ?: "Not selected"
          Text(
            text = "Destination: $folderLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (autoBackupSettings.destinationTreeUri != null && !hasAccess) {
            Text(
              text = "Destination access was lost. Please choose the folder again to grant access.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Medium,
            )
          }

          autoBackupSettings.lastSuccessEpochMs?.let { ts ->
            val formatted = remember(ts) {
              SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
            }
            Text(
              text = "Last successful backup: $formatted",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
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
              onClick = { autoBackupTreeLauncher.launch(null) },
              modifier = Modifier.weight(1f),
            ) {
              Text("Choose folder")
            }
            OutlinedButton(
              onClick = { showAutoBackupPasswordDialog = true },
              modifier = Modifier.weight(1f),
            ) {
              Text(if (autoBackupSettings.password.isEmpty()) "Set password" else "Change password")
            }
          }
          Button(
            onClick = {
              isWorking = true
              errorMessage = null
              statusMessage = null
              scope.launch {
                val res = autoBackupManager.runBackupNow()
                res.onSuccess { fileName ->
                  statusMessage = "Automatic backup verified: $fileName"
                }.onFailure { err ->
                  errorMessage = err.message ?: "Automatic backup failed."
                }
                isWorking = false
              }
            },
            enabled = !isWorking && autoBackupSettings.destinationTreeUri != null && autoBackupSettings.password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Run backup now")
          }
        }
      }

      // CSV Export
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
            text = "Export a readable directory snapshot (UTF-8). Use encrypted backup for photos, full role history, and settings.",
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
  }

  if (showAutoBackupPasswordDialog) {
    CreateBackupPasswordDialog(
      onDismiss = { showAutoBackupPasswordDialog = false },
      onConfirm = { password ->
        showAutoBackupPasswordDialog = false
        scope.launch {
          autoBackupManager.configureSchedule(
            enabled = true,
            destinationUri = null,
            destinationDisplayName = null,
            password = password,
          )
          statusMessage = "Automatic backup password saved and daily schedule enabled."
        }
      },
    )
  }

  if (showCreatePasswordDialog) {
    CreateBackupPasswordDialog(
      onDismiss = { showCreatePasswordDialog = false },
      onConfirm = { password ->
        showCreatePasswordDialog = false
        pendingExportPassword = password
        val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        createBackupLauncher.launch("nexus-backup-$dateStamp.nxbk")
      },
    )
  }

  if (showRestorePasswordDialog) {
    RestorePasswordDialog(
      onDismiss = {
        showRestorePasswordDialog = false
        pendingRestoreBytes = null
        statusMessage = "Restore cancelled."
      },
      onConfirm = { password ->
        val bytes = pendingRestoreBytes ?: return@RestorePasswordDialog
        showRestorePasswordDialog = false
        pendingRestoreBytes = null
        isWorking = true
        errorMessage = null
        scope.launch {
          runCatching {
            repository.inspectBackup(bytes, password)
          }.onSuccess { preview ->
            restoreMode = RestoreMode.MERGE
            keepDuplicateIds = emptySet()
            replaceConfirmStep = FullReplaceConfirmationStep.NONE
            restorePreview = preview
          }.onFailure { err ->
            errorMessage = when (err) {
              is AEADBadTagException -> "Incorrect password or damaged backup file. Existing data was not changed."
              else -> (err.message ?: "Invalid or damaged backup file.") + " Existing data was not changed."
            }
          }
          isWorking = false
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

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (mode == RestoreMode.MERGE) {
            Text(
              text = "To add: ${mergePreview.toAdd.size} · Matched by ID: ${mergePreview.matchedById.size} · Local-only kept: ${mergePreview.localOnlyCount} · Duplicates: ${mergePreview.duplicates.size}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )

            mergePreview.toAdd.forEach { profile ->
              Text(
                text = "+ Add: ${profile.name}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
            mergePreview.matchedById.forEach { (local, _) ->
              Text(
                text = "• Merge by ID: ${local.name} (keep local, fill blanks)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            if (mergePreview.duplicates.isNotEmpty()) {
              Text(
                text = "Possible duplicates",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
              )
              mergePreview.duplicates.forEach { item ->
                val keep = item.incomingProfile.id in keepDuplicateIds
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                  Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Text(
                      text = "${item.incomingProfile.name} matches ${item.matchedLocalProfile.name} (${item.matchedField.label})",
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      FilterChip(
                        selected = !keep,
                        onClick = { onToggleDuplicateKeep(item.incomingProfile.id, false) },
                        label = { Text("Skip") },
                      )
                      FilterChip(
                        selected = keep,
                        onClick = { onToggleDuplicateKeep(item.incomingProfile.id, true) },
                        label = { Text("Keep separately") },
                      )
                    }
                  }
                }
              }
            }
          } else {
            Text(
              text = "Full replacement will overwrite your $localCount current profiles, photos, tags, reusable groups, and owner card with the ${backupPreview.profiles.size} profiles in this backup.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
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
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Enter backup password") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Enter the password used when this backup was created.")
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
          isError = error != null,
          supportingText = error?.let { msg -> { Text(msg) } },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (password.isEmpty()) {
            error = "Password is required"
          } else {
            onConfirm(password)
          }
        },
      ) {
        Text("Verify")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
