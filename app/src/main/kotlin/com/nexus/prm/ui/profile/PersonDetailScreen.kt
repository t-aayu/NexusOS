package com.nexus.prm.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import com.nexus.prm.data.ContactShareHelper
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.IntroductionRecord
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.PostingEntry
import com.nexus.prm.data.PostingValidation
import com.nexus.prm.ui.components.DatePickerField
import com.nexus.prm.ui.components.ProfileAvatar
import com.nexus.prm.ui.components.WheelDatePickerDialog
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonDetailScreen(
  profileId: String,
  repository: DirectoryRepository,
  onBack: () -> Unit,
  onEdit: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val profiles by repository.profiles.collectAsStateWithLifecycle()
  val allGroups by repository.groups.collectAsStateWithLifecycle()
  val profile = profiles.firstOrNull { it.id == profileId }
  val memberGroups = remember(allGroups, profileId) {
    allGroups.filter { profileId in it.memberIds }
  }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showGroupsDialog by remember { mutableStateOf(false) }
  var editingPosting by remember { mutableStateOf<PostingEntry?>(null) }
  var showAddPostingDialog by remember { mutableStateOf(false) }
  var showAddIntroDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        title = { Text("Profile", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (profile != null) {
            IconButton(onClick = { ContactShareHelper.shareContact(context, profile) }) {
              Icon(Icons.Outlined.Share, contentDescription = "Share contact")
            }
            IconButton(onClick = { scope.launch { repository.toggleFavourite(profile.id) } }) {
              Icon(
                imageVector = if (profile.isFavourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (profile.isFavourite) "Remove favourite" else "Mark favourite",
                tint = if (profile.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(onClick = { onEdit(profile.id) }) {
              Icon(Icons.Outlined.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { showDeleteDialog = true }) {
              Icon(Icons.Outlined.Delete, contentDescription = "Delete")
            }
          }
        },
      )
    },
  ) { innerPadding ->
    if (profile == null) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("Profile not found", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onBack) { Text("Back to People") }
      }
      return@Scaffold
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        ProfileAvatar(
          name = profile.name,
          photoFileName = profile.photoFileName,
          loadPhotoBytes = repository::loadPhotoBytes,
          size = 104.dp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = profile.name,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
        )
        val currentRole = profile.currentPosting()
        val roleHeadline = listOfNotNull(
          currentRole?.designation?.takeIf { it.isNotBlank() } ?: profile.designation.takeIf { it.isNotBlank() },
          currentRole?.organisation?.takeIf { it.isNotBlank() } ?: profile.organisation.takeIf { it.isNotBlank() },
          currentRole?.location?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (roleHeadline.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = roleHeadline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        profile.categoryLabel()?.let { categoryLabel ->
          Spacer(modifier = Modifier.height(6.dp))
          AssistChip(
            onClick = {},
            label = { Text(categoryLabel) },
            colors = AssistChipDefaults.assistChipColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            border = null,
          )
        }
        val hasPhone = profile.phone.isNotBlank()
        val hasEmail = profile.email.isNotBlank()
        if (hasPhone || hasEmail) {
          Spacer(modifier = Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasPhone) {
              Button(
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                onClick = {
                  val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(profile.phone)}"))
                  runCatching { context.startActivity(intent) }.onFailure {
                    Toast.makeText(context, "No dialler app available", Toast.LENGTH_SHORT).show()
                  }
                },
              ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Call")
              }
            }
            if (hasEmail) {
              FilledTonalButton(
                colors = ButtonDefaults.filledTonalButtonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                onClick = {
                  val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(profile.email)}"))
                  runCatching { context.startActivity(intent) }.onFailure {
                    Toast.makeText(context, "No email app available", Toast.LENGTH_SHORT).show()
                  }
                },
              ) {
                Icon(Icons.Filled.Email, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Email")
              }
            }
          }
        }
      }

      val quickDetails = listOfNotNull(
        "Phone" to profile.phone,
        "Where we met" to profile.whereWeMet,
      ).filter { it.second.isNotBlank() }

      if (quickDetails.isNotEmpty()) {
        DetailSectionCard(title = "Contact & Context", rows = quickDetails)
      }

      val moreDetails = listOf(
        "Email" to profile.email,
        "Organisation" to profile.organisation,
        "Designation" to profile.designation,
        "Service" to profile.service,
        "Cadre" to profile.cadre,
        "Service batch" to profile.serviceBatch,
        "Foundation Course batch" to profile.foundationCourseBatch,
        "Training group" to profile.trainingGroup,
      ).filter { it.second.isNotBlank() }

      if (moreDetails.isNotEmpty()) {
        DetailSectionCard(title = "More details", rows = moreDetails)
      }

      if (profile.otherDetails.isNotBlank()) {
        DetailSectionCard(
          title = "Other details",
          rows = listOf("Notes" to profile.otherDetails),
        )
      }

      // Posting and role history
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
            Text(
              text = "Posting & role history",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { showAddPostingDialog = true }) {
              Icon(Icons.Filled.Add, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add")
            }
          }
          if (profile.postings.isEmpty()) {
            Text(
              text = "No postings recorded yet.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            val orderedPostings = profile.postings.sortedByDescending { it.isCurrent }
            orderedPostings.forEachIndexed { idx, entry ->
              if (idx > 0) HorizontalDivider()
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  val title = listOf(entry.designation, entry.organisation)
                    .filter { it.isNotBlank() }
                    .joinToString(" — ")
                    .ifBlank { "Role" }
                  Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (entry.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                  )
                  if (entry.isCurrent) {
                    AssistChip(
                      onClick = {},
                      label = { Text("Current") },
                      colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      ),
                      border = null,
                    )
                  }
                }
                val meta = listOfNotNull(
                  entry.location.takeIf { it.isNotBlank() },
                  when {
                    entry.startDate.isNotBlank() && entry.endDate.isNotBlank() ->
                      "${PostingValidation.formatForDisplay(entry.startDate)} to ${PostingValidation.formatForDisplay(entry.endDate)}"
                    entry.startDate.isNotBlank() -> "From ${PostingValidation.formatForDisplay(entry.startDate)}"
                    entry.endDate.isNotBlank() -> "Until ${PostingValidation.formatForDisplay(entry.endDate)}"
                    else -> null
                  },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                  Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  TextButton(onClick = { editingPosting = entry }) { Text("Edit") }
                  if (!entry.isCurrent) {
                    TextButton(
                      onClick = { scope.launch { repository.setCurrentPosting(profile.id, entry.id) } },
                    ) {
                      Text("Mark current")
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Introductions
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
            Text(
              text = "Introductions",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { showAddIntroDialog = true }) {
              Icon(Icons.Filled.Add, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add")
            }
          }
          if (profile.introductions.isEmpty()) {
            Text(
              text = "No introductions recorded yet.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            profile.introductions.forEachIndexed { idx, intro ->
              if (idx > 0) HorizontalDivider()
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "Introduced by ${intro.introducerName}",
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.Medium,
                )
                val sub = listOfNotNull(
                  intro.date.takeIf { it.isNotBlank() }?.let { PostingValidation.formatForDisplay(it) },
                  intro.contextNote.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                  Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }

      if (profile.tags.isNotEmpty()) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Tags",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              profile.tags.forEach { tag ->
                AssistChip(
                  onClick = {},
                  label = { Text(tag) },
                  colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  ),
                  border = null,
                )
              }
            }
          }
        }
      }

      if (allGroups.isNotEmpty()) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Groups",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
              )
              TextButton(onClick = { showGroupsDialog = true }) {
                Text("Edit")
              }
            }
            if (memberGroups.isEmpty()) {
              Text(
                text = "Not in any reusable group",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                memberGroups.forEach { group ->
                  AssistChip(
                    onClick = { showGroupsDialog = true },
                    label = { Text(group.name) },
                    colors = AssistChipDefaults.assistChipColors(
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    border = null,
                  )
                }
              }
            }
          }
        }
      }
    }

    if (showGroupsDialog) {
      AlertDialog(
        onDismissRequest = { showGroupsDialog = false },
        title = { Text("Group memberships") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            allGroups.forEach { group ->
              val isMember = profile.id in group.memberIds
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = isMember,
                  onCheckedChange = { checked ->
                    val updated = if (checked) group.memberIds + profile.id else group.memberIds - profile.id
                    scope.launch { repository.setGroupMembers(group.id, updated) }
                  },
                )
                Text(group.name, style = MaterialTheme.typography.bodyLarge)
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showGroupsDialog = false }) { Text("Done") }
        },
      )
    }

    if (showDeleteDialog) {
      AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Remove profile?") },
        text = { Text("Remove ${profile.name} from your directory? This cannot be undone.") },
        confirmButton = {
          TextButton(
            onClick = {
              showDeleteDialog = false
              scope.launch {
                repository.deleteProfile(profile.id)
                onBack()
              }
            },
          ) {
            Text("Remove", color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteDialog = false }) {
            Text("Cancel")
          }
        },
      )
    }

    if (showAddPostingDialog || editingPosting != null) {
      PostingEditorDialog(
        initial = editingPosting,
        onDismiss = {
          showAddPostingDialog = false
          editingPosting = null
        },
        onSave = { entry ->
          showAddPostingDialog = false
          editingPosting = null
          scope.launch { repository.addOrUpdatePosting(profile.id, entry) }
        },
      )
    }

    if (showAddIntroDialog) {
      AddIntroductionDialog(
        otherProfiles = profiles.filter { it.id != profile.id },
        onDismiss = { showAddIntroDialog = false },
        onSave = { intro ->
          showAddIntroDialog = false
          scope.launch { repository.addIntroduction(profile.id, intro) }
        },
      )
    }
  }
}

@Composable
private fun PostingEditorDialog(
  initial: PostingEntry?,
  onDismiss: () -> Unit,
  onSave: (PostingEntry) -> Unit,
) {
  var organisation by remember { mutableStateOf(initial?.organisation.orEmpty()) }
  var designation by remember { mutableStateOf(initial?.designation.orEmpty()) }
  var location by remember { mutableStateOf(initial?.location.orEmpty()) }
  var startDate by remember { mutableStateOf(PostingValidation.formatForDisplay(initial?.startDate.orEmpty())) }
  var endDate by remember { mutableStateOf(PostingValidation.formatForDisplay(initial?.endDate.orEmpty())) }
  var isCurrent by remember { mutableStateOf(initial?.isCurrent ?: true) }
  var errorText by remember { mutableStateOf<String?>(null) }
  var showStartPicker by remember { mutableStateOf(false) }
  var showEndPicker by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "Add posting" else "Edit posting") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = organisation,
          onValueChange = { organisation = it; errorText = null },
          label = { Text("Organisation") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = designation,
          onValueChange = { designation = it; errorText = null },
          label = { Text("Designation") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = location,
          onValueChange = { location = it },
          label = { Text("Location") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        DatePickerField(
          label = "Start date (DD-MM-YYYY, optional)",
          value = startDate,
          onClick = { showStartPicker = true },
          onClear = { startDate = ""; errorText = null },
          modifier = Modifier.fillMaxWidth(),
        )
        DatePickerField(
          label = "End date (DD-MM-YYYY, optional)",
          value = endDate,
          onClick = { showEndPicker = true },
          onClear = { endDate = ""; errorText = null },
          isError = errorText != null,
          supportingText = errorText?.let { msg -> { Text(msg) } },
          modifier = Modifier.fillMaxWidth(),
        )
        if (showStartPicker) {
          WheelDatePickerDialog(
            title = "Select start date",
            initialDate = startDate,
            onDismiss = { showStartPicker = false },
            onConfirm = { picked ->
              startDate = picked
              errorText = null
              showStartPicker = false
            },
          )
        }
        if (showEndPicker) {
          WheelDatePickerDialog(
            title = "Select end date",
            initialDate = endDate,
            onDismiss = { showEndPicker = false },
            onConfirm = { picked ->
              endDate = picked
              errorText = null
              showEndPicker = false
            },
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = isCurrent, onCheckedChange = { isCurrent = it })
          Text("Mark as current role")
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when {
            organisation.isBlank() && designation.isBlank() -> {
              errorText = "Enter an organisation or designation"
            }
            !PostingValidation.isValidDateRange(startDate, endDate) -> {
              errorText = "End date cannot be earlier than start date"
            }
            else -> {
              onSave(
                PostingEntry(
                  id = initial?.id ?: UUID.randomUUID().toString(),
                  organisation = organisation,
                  designation = designation,
                  location = location,
                  startDate = startDate,
                  endDate = endDate,
                  isCurrent = isCurrent,
                ),
              )
            }
          }
        },
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun AddIntroductionDialog(
  otherProfiles: List<PersonProfile>,
  onDismiss: () -> Unit,
  onSave: (IntroductionRecord) -> Unit,
) {
  var selectedProfile by remember { mutableStateOf<PersonProfile?>(null) }
  var typedName by remember { mutableStateOf("") }
  var date by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var pickerExpanded by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Record introduction") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (otherProfiles.isNotEmpty()) {
          androidx.compose.foundation.layout.Box {
            OutlinedButton(
              onClick = { pickerExpanded = true },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(selectedProfile?.let { "Linked: ${it.name}" } ?: "Choose a Nexus profile (optional)")
            }
            DropdownMenu(
              expanded = pickerExpanded,
              onDismissRequest = { pickerExpanded = false },
            ) {
              DropdownMenuItem(
                text = { Text("None (type name)") },
                onClick = {
                  selectedProfile = null
                  pickerExpanded = false
                },
              )
              otherProfiles.forEach { candidate ->
                DropdownMenuItem(
                  text = { Text(candidate.name) },
                  onClick = {
                    selectedProfile = candidate
                    typedName = candidate.name
                    errorText = null
                    pickerExpanded = false
                  },
                )
              }
            }
          }
        }

        OutlinedTextField(
          value = typedName,
          onValueChange = {
            typedName = it
            if (selectedProfile != null && it != selectedProfile?.name) {
              selectedProfile = null
            }
            errorText = null
          },
          label = { Text("Introducer name *") },
          singleLine = true,
          isError = errorText != null,
          supportingText = errorText?.let { msg -> { Text(msg) } },
          modifier = Modifier.fillMaxWidth(),
        )
        DatePickerField(
          label = "Date (DD-MM-YYYY, optional)",
          value = date,
          onClick = { showDatePicker = true },
          onClear = { date = "" },
          modifier = Modifier.fillMaxWidth(),
        )
        if (showDatePicker) {
          WheelDatePickerDialog(
            title = "Select introduction date",
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
              date = picked
              showDatePicker = false
            },
          )
        }
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("Context note (optional)") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val finalName = (selectedProfile?.name ?: typedName).trim()
          if (finalName.isEmpty()) {
            errorText = "Introducer name is required"
          } else {
            onSave(
              IntroductionRecord(
                id = UUID.randomUUID().toString(),
                introducerProfileId = selectedProfile?.id,
                introducerName = finalName,
                date = date,
                contextNote = note,
              ),
            )
          }
        },
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun DetailSectionCard(
  title: String,
  rows: List<Pair<String, String>>,
) {
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
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
      )
      rows.forEach { (label, value) ->
        Column {
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
          )
        }
      }
    }
  }
}
