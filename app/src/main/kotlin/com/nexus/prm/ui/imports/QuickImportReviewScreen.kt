package com.nexus.prm.ui.imports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.DuplicateMatch
import com.nexus.prm.data.DuplicateMatcher
import com.nexus.prm.data.LabeledContactValue
import com.nexus.prm.data.ParsedVCardContact
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.QuickCaptureResult
import com.nexus.prm.data.QuickImportParser
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ImportTargetMode {
  CREATE_NEW,
  ADD_TO_EXISTING,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickImportReviewScreen(
  repository: DirectoryRepository,
  initialText: String?,
  vCardUriString: String?,
  openFilePickerOnStart: Boolean,
  onBack: () -> Unit,
  onSaved: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val allProfiles by repository.profiles.collectAsStateWithLifecycle()

  val isVCardMode = vCardUriString != null || openFilePickerOnStart
  var activeUriString by remember(vCardUriString) { mutableStateOf(vCardUriString) }
  var vCardError by remember { mutableStateOf<String?>(null) }
  var parsedVCard by remember { mutableStateOf<ParsedVCardContact?>(null) }

  var textKind by remember { mutableStateOf<QuickCaptureResult.Kind?>(null) }
  var textError by remember { mutableStateOf<String?>(null) }

  var editableName by remember { mutableStateOf("") }
  var editableValue by remember { mutableStateOf("") }
  var editableLabel by remember { mutableStateOf(LabeledContactValue.DEFAULT_LABEL) }
  var showCustomLabelDialog by remember { mutableStateOf(false) }
  var customLabelInput by remember { mutableStateOf("") }

  var targetMode by remember { mutableStateOf(ImportTargetMode.CREATE_NEW) }
  var existingSearchQuery by remember { mutableStateOf("") }
  var selectedExistingId by remember { mutableStateOf<String?>(null) }
  var pendingDuplicate by remember { mutableStateOf<DuplicateMatch?>(null) }
  var isSaving by remember { mutableStateOf(false) }
  var hasLaunchedPicker by remember { mutableStateOf(false) }

  val vCardPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri != null) {
      activeUriString = uri.toString()
      vCardError = null
    } else if (parsedVCard == null && vCardError == null) {
      onBack()
    }
  }

  LaunchedEffect(openFilePickerOnStart) {
    if (openFilePickerOnStart && activeUriString == null && !hasLaunchedPicker) {
      hasLaunchedPicker = true
      vCardPickerLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "*/*"))
    }
  }

  LaunchedEffect(initialText, isVCardMode) {
    if (!isVCardMode) {
      when (val res = QuickImportParser.parseSelectedText(initialText)) {
        is QuickCaptureResult.SingleValue -> {
          textKind = res.kind
          editableValue = res.value
          textError = null
        }
        is QuickCaptureResult.Invalid -> {
          textKind = null
          textError = res.message
        }
      }
    }
  }

  LaunchedEffect(activeUriString) {
    val uriStr = activeUriString ?: return@LaunchedEffect
    val parsedResult = withContext(Dispatchers.IO) {
      runCatching {
        val raw = context.contentResolver.openInputStream(Uri.parse(uriStr))?.use {
          it.readBytes().toString(Charsets.UTF_8)
        } ?: error("Could not read the selected vCard file.")
        QuickImportParser.parseSingleVCard(raw).getOrThrow()
      }
    }
    parsedResult.onSuccess { parsed ->
      parsedVCard = parsed
      editableName = parsed.name
      vCardError = null
    }.onFailure { err ->
      parsedVCard = null
      vCardError = err.message ?: "Could not parse the selected vCard file."
    }
  }

  fun candidatePhones(): List<LabeledContactValue> = when {
    isVCardMode -> parsedVCard?.phones.orEmpty()
    textKind == QuickCaptureResult.Kind.PHONE -> {
      val trimmed = editableValue.trim()
      if (trimmed.isEmpty()) emptyList() else listOf(LabeledContactValue(trimmed, editableLabel, isPrimary = true))
    }
    else -> emptyList()
  }

  fun candidateEmails(): List<LabeledContactValue> = when {
    isVCardMode -> parsedVCard?.emails.orEmpty()
    textKind == QuickCaptureResult.Kind.EMAIL -> {
      val trimmed = editableValue.trim()
      if (trimmed.isEmpty()) emptyList() else listOf(LabeledContactValue(trimmed, editableLabel, isPrimary = true))
    }
    else -> emptyList()
  }

  fun commitSave() {
    if (isSaving) return
    val newPhones = candidatePhones()
    val newEmails = candidateEmails()
    isSaving = true
    scope.launch {
      if (targetMode == ImportTargetMode.CREATE_NEW) {
        val now = System.currentTimeMillis()
        val card = parsedVCard
        val nameToSave = editableName.trim()
        val created = repository.saveProfile(
          PersonProfile(
            id = UUID.randomUUID().toString(),
            name = nameToSave,
            phone = newPhones.firstOrNull { it.isPrimary }?.value ?: newPhones.firstOrNull()?.value.orEmpty(),
            phones = newPhones,
            email = newEmails.firstOrNull { it.isPrimary }?.value ?: newEmails.firstOrNull()?.value.orEmpty(),
            emails = newEmails,
            organisation = card?.organisation.orEmpty(),
            designation = card?.designation.orEmpty(),
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
          ),
        )
        isSaving = false
        onSaved(created.id)
      } else {
        val existing = allProfiles.firstOrNull { it.id == selectedExistingId }
        if (existing == null) {
          isSaving = false
          return@launch
        }
        val mergedPhones = appendUniqueContactEntries(
          existing.resolvedPhones(),
          newPhones,
          DuplicateMatcher::normalizePhone,
        )
        val mergedEmails = appendUniqueContactEntries(
          existing.resolvedEmails(),
          newEmails,
          DuplicateMatcher::normalizeEmail,
        )
        val card = parsedVCard
        val updated = repository.saveProfile(
          existing.copy(
            phone = mergedPhones.firstOrNull { it.isPrimary }?.value ?: existing.primaryPhone(),
            phones = mergedPhones,
            email = mergedEmails.firstOrNull { it.isPrimary }?.value ?: existing.primaryEmail(),
            emails = mergedEmails,
            organisation = existing.organisation.ifBlank { card?.organisation.orEmpty() },
            designation = existing.designation.ifBlank { card?.designation.orEmpty() },
            updatedAtEpochMs = System.currentTimeMillis(),
          ),
        )
        isSaving = false
        onSaved(updated.id)
      }
    }
  }

  fun performSave() {
    if (isSaving) return
    if (targetMode == ImportTargetMode.CREATE_NEW && editableName.trim().isEmpty()) {
      return
    }
    if (targetMode == ImportTargetMode.ADD_TO_EXISTING && selectedExistingId == null) {
      return
    }

    val excludeId = if (targetMode == ImportTargetMode.ADD_TO_EXISTING) selectedExistingId else null
    val match = DuplicateMatcher.findMatch(
      phones = candidatePhones().map { it.value },
      emails = candidateEmails().map { it.value },
      candidates = allProfiles,
      excludeProfileId = excludeId,
    )
    if (match != null) {
      pendingDuplicate = match
      return
    }
    commitSave()
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
        title = {
          Text(
            text = if (isVCardMode) "Import vCard" else "Add to Nexus",
            fontWeight = FontWeight.SemiBold,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    val errorMsg = if (isVCardMode) vCardError else textError
    if (errorMsg != null) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = if (isVCardMode) "Unable to import vCard" else "Unable to capture selection",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.error,
        )
        Text(text = errorMsg, style = MaterialTheme.typography.bodyMedium)
        if (isVCardMode) {
          Button(
            onClick = { vCardPickerLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Pick another vCard")
          }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
          Text("Cancel")
        }
      }
      return@Scaffold
    }

    if (isVCardMode && parsedVCard == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        Text("Waiting for vCard file…", style = MaterialTheme.typography.bodyMedium)
      }
      return@Scaffold
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = targetMode == ImportTargetMode.CREATE_NEW,
          onClick = { targetMode = ImportTargetMode.CREATE_NEW },
          label = { Text("Create new contact") },
        )
        FilterChip(
          selected = targetMode == ImportTargetMode.ADD_TO_EXISTING,
          onClick = { targetMode = ImportTargetMode.ADD_TO_EXISTING },
          label = { Text("Add to existing contact") },
        )
      }

      if (targetMode == ImportTargetMode.CREATE_NEW) {
        OutlinedTextField(
          value = editableName,
          onValueChange = { editableName = it },
          label = { Text("Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (!isVCardMode) {
        OutlinedTextField(
          value = editableValue,
          onValueChange = { editableValue = it },
          label = {
            Text(if (textKind == QuickCaptureResult.Kind.EMAIL) "Email" else "Phone number")
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        var labelExpanded by remember { mutableStateOf(false) }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Label:", style = MaterialTheme.typography.labelLarge)
          Box {
            AssistChip(onClick = { labelExpanded = true }, label = { Text(editableLabel) })
            DropdownMenu(
              expanded = labelExpanded,
              onDismissRequest = { labelExpanded = false },
            ) {
              LabeledContactValue.PRESET_LABELS.forEach { preset ->
                DropdownMenuItem(
                  text = { Text(preset) },
                  onClick = {
                    editableLabel = preset
                    labelExpanded = false
                  },
                )
              }
              DropdownMenuItem(
                text = { Text("Custom…") },
                onClick = {
                  labelExpanded = false
                  customLabelInput = ""
                  showCustomLabelDialog = true
                },
              )
            }
          }
        }
      } else {
        parsedVCard?.let { card ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text("vCard details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
              card.phones.forEach { phone ->
                val badge = if (phone.isPrimary && card.phones.size > 1) " · Primary" else ""
                Text("Phone (${phone.label}$badge): ${phone.value}", style = MaterialTheme.typography.bodySmall)
              }
              card.emails.forEach { email ->
                val badge = if (email.isPrimary && card.emails.size > 1) " · Primary" else ""
                Text("Email (${email.label}$badge): ${email.value}", style = MaterialTheme.typography.bodySmall)
              }
              if (card.organisation.isNotBlank()) {
                Text("Organisation: ${card.organisation}", style = MaterialTheme.typography.bodySmall)
              }
              if (card.designation.isNotBlank()) {
                Text("Designation: ${card.designation}", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }

      if (targetMode == ImportTargetMode.ADD_TO_EXISTING) {
        OutlinedTextField(
          value = existingSearchQuery,
          onValueChange = { existingSearchQuery = it },
          label = { Text("Search existing contacts") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        val filteredExisting = remember(allProfiles, existingSearchQuery) {
          DirectorySearch.filterAndSort(
            profiles = allProfiles,
            query = existingSearchQuery,
            selectedCategory = null,
            selectedTrainingGroup = null,
            favouritesOnly = false,
            recentlyAddedOnly = false,
          )
        }
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(filteredExisting, key = { it.id }) { candidate ->
            val selected = candidate.id == selectedExistingId
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedExistingId = candidate.id },
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
              ),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(candidate.name, fontWeight = FontWeight.Medium)
                  val sub = candidate.primaryPhone().ifBlank { candidate.primaryEmail() }
                  if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                if (selected) {
                  Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }

        val chosen = allProfiles.firstOrNull { it.id == selectedExistingId }
        if (chosen != null) {
          val newPhonesToAdd = findNewEntries(chosen.resolvedPhones(), candidatePhones(), DuplicateMatcher::normalizePhone)
          val newEmailsToAdd = findNewEntries(chosen.resolvedEmails(), candidateEmails(), DuplicateMatcher::normalizeEmail)
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Preview for ${chosen.name}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
              if (newPhonesToAdd.isEmpty() && newEmailsToAdd.isEmpty()) {
                Text("All imported numbers and emails are already saved on this contact.", style = MaterialTheme.typography.bodySmall)
              } else {
                newPhonesToAdd.forEach { Text("+ Phone (${it.label}): ${it.value}", style = MaterialTheme.typography.bodySmall) }
                newEmailsToAdd.forEach { Text("+ Email (${it.label}): ${it.value}", style = MaterialTheme.typography.bodySmall) }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      val canSave = !isSaving && when (targetMode) {
        ImportTargetMode.CREATE_NEW -> editableName.trim().isNotEmpty() &&
          (candidatePhones().isNotEmpty() || candidateEmails().isNotEmpty() || isVCardMode)
        ImportTargetMode.ADD_TO_EXISTING -> selectedExistingId != null
      }
      Button(
        onClick = ::performSave,
        enabled = canSave,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          if (targetMode == ImportTargetMode.CREATE_NEW) "Save new contact" else "Append to contact",
        )
      }
      OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
      }
    }
  }

  if (showCustomLabelDialog) {
    AlertDialog(
      onDismissRequest = { showCustomLabelDialog = false },
      title = { Text("Custom label") },
      text = {
        OutlinedTextField(
          value = customLabelInput,
          onValueChange = { customLabelInput = it },
          label = { Text("Label") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            editableLabel = customLabelInput.trim().ifEmpty { LabeledContactValue.DEFAULT_LABEL }
            showCustomLabelDialog = false
          },
        ) { Text("Save") }
      },
      dismissButton = {
        TextButton(onClick = { showCustomLabelDialog = false }) { Text("Cancel") }
      },
    )
  }

  pendingDuplicate?.let { match ->
    AlertDialog(
      onDismissRequest = { pendingDuplicate = null },
      title = { Text("Possible duplicate") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            "This matches ${match.existingProfile.name} on ${match.matchedField.label}.",
            fontWeight = FontWeight.Medium,
          )
          if (match.existingProfile.primaryPhone().isNotBlank()) {
            Text("Phone: ${match.existingProfile.primaryPhone()}", style = MaterialTheme.typography.bodySmall)
          }
          if (match.existingProfile.primaryEmail().isNotBlank()) {
            Text("Email: ${match.existingProfile.primaryEmail()}", style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            pendingDuplicate = null
            commitSave()
          },
        ) { Text("Keep separately") }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = {
              pendingDuplicate = null
              onBack()
            },
          ) { Text("Skip") }
          TextButton(onClick = { pendingDuplicate = null }) { Text("Review") }
        }
      },
    )
  }
}

private fun findNewEntries(
  existing: List<LabeledContactValue>,
  incoming: List<LabeledContactValue>,
  normalizer: (String) -> String,
): List<LabeledContactValue> {
  val existingKeys = existing.mapNotNull { normalizer(it.value).takeIf { k -> k.isNotEmpty() } }.toSet()
  return incoming.filter { entry ->
    val key = normalizer(entry.value)
    key.isNotEmpty() && key !in existingKeys
  }
}

private fun appendUniqueContactEntries(
  existing: List<LabeledContactValue>,
  incoming: List<LabeledContactValue>,
  normalizer: (String) -> String,
): List<LabeledContactValue> {
  if (existing.isEmpty()) return incoming
  val toAdd = findNewEntries(existing, incoming, normalizer).map { it.copy(isPrimary = false) }
  return existing + toAdd
}
