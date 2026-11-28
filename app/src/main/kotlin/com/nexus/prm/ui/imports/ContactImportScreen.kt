package com.nexus.prm.ui.imports

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.AndroidContactsReader
import com.nexus.prm.data.DeviceContact
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.DuplicateMatch
import com.nexus.prm.data.DuplicateMatcher
import com.nexus.prm.data.PersonProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ContactImportStep {
  SELECT,
  REVIEW,
}

private data class ReviewedContact(
  val contact: DeviceContact,
  val chosenPhone: String,
  val chosenEmail: String,
  val keepIfDuplicate: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactImportScreen(
  repository: DirectoryRepository,
  onBack: () -> Unit,
  onImported: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val existingProfiles by repository.profiles.collectAsStateWithLifecycle()

  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  var isLoading by remember { mutableStateOf(false) }
  var contacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
  var query by remember { mutableStateOf("") }
  val selectedIds = remember { mutableStateMapOf<String, Boolean>() }

  var step by remember { mutableStateOf(ContactImportStep.SELECT) }
  var reviewItems by remember { mutableStateOf<List<ReviewedContact>>(emptyList()) }
  var isSaving by remember { mutableStateOf(false) }
  var saveFailed by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasPermission = granted
  }

  LaunchedEffect(hasPermission) {
    if (hasPermission && contacts.isEmpty()) {
      isLoading = true
      contacts = withContext(Dispatchers.IO) {
        AndroidContactsReader.readContacts(context)
      }
      isLoading = false
    }
  }

  val filteredContacts = remember(contacts, query) {
    val q = query.trim()
    if (q.isEmpty()) contacts
    else contacts.filter {
      it.displayName.contains(q, ignoreCase = true) ||
        it.phones.any { p -> p.contains(q, ignoreCase = true) } ||
        it.emails.any { e -> e.contains(q, ignoreCase = true) }
    }
  }

  val selectedCount = selectedIds.values.count { it }

  BackHandler(enabled = step == ContactImportStep.REVIEW) {
    if (!isSaving) {
      saveFailed = false
      step = ContactImportStep.SELECT
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
        title = {
          Text(
            if (step == ContactImportStep.SELECT) "Import contacts" else "Review selection",
            fontWeight = FontWeight.SemiBold,
          )
        },
        navigationIcon = {
          IconButton(
            enabled = !isSaving,
            onClick = {
              if (step == ContactImportStep.REVIEW) {
                saveFailed = false
                step = ContactImportStep.SELECT
              } else {
                onBack()
              }
            },
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (step == ContactImportStep.SELECT && selectedCount > 0) {
            TextButton(
              onClick = {
                reviewItems = contacts
                  .filter { selectedIds[it.contactId] == true }
                  .map { c ->
                    ReviewedContact(
                      contact = c,
                      chosenPhone = c.phones.firstOrNull().orEmpty(),
                      chosenEmail = c.emails.firstOrNull().orEmpty(),
                    )
                  }
                step = ContactImportStep.REVIEW
              },
            ) {
              Text("Review ($selectedCount)", fontWeight = FontWeight.SemiBold)
            }
          }
        },
      )
    },
  ) { innerPadding ->
    if (!hasPermission) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Contacts permission needed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Nexus only reads the contacts you choose to copy and never modifies your Android contacts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
            Text("Allow read-only access")
          }
        }
      }
      return@Scaffold
    }

    if (isLoading) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
      return@Scaffold
    }

    if (step == ContactImportStep.SELECT) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          shape = RoundedCornerShape(16.dp),
          placeholder = { Text("Filter Android contacts") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          singleLine = true,
        )
        LazyColumn(
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(filteredContacts, key = { it.contactId }) { contact ->
            val checked = selectedIds[contact.contactId] == true
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedIds[contact.contactId] = !checked },
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = checked,
                  onCheckedChange = { selectedIds[contact.contactId] = it },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(contact.displayName, fontWeight = FontWeight.SemiBold)
                  val subtitle = listOfNotNull(
                    contact.phones.firstOrNull(),
                    contact.emails.firstOrNull(),
                  ).joinToString(" · ")
                  if (subtitle.isNotEmpty()) {
                    Text(
                      text = subtitle,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }
      }
    } else {
      val duplicateMatches = remember(reviewItems, existingProfiles) {
        computeBatchMatches(reviewItems, existingProfiles)
      }

      val approvedItems = reviewItems.filterIndexed { index, item ->
        item.contact.displayName.isNotBlank() && (index !in duplicateMatches || item.keepIfDuplicate)
      }
      val skippedCount = reviewItems.size - approvedItems.size

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        LazyColumn(
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            ImportReviewSummary(approvedItems.size, skippedCount, duplicateMatches.size)
          }
          items(reviewItems.indices.toList(), key = { reviewItems[it].contact.contactId }) { index ->
            val item = reviewItems[index]
            val match = duplicateMatches[index]
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
                  text = item.contact.displayName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )

                if (item.contact.phones.size > 1) {
                  Text("Choose phone number:", style = MaterialTheme.typography.labelMedium)
                  item.contact.phones.forEach { phoneOption ->
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSaving) {
                          reviewItems = reviewItems.toMutableList().also {
                            it[index] = item.copy(chosenPhone = phoneOption)
                          }
                        },
                    ) {
                      RadioButton(
                        enabled = !isSaving,
                        selected = item.chosenPhone == phoneOption,
                        onClick = {
                          reviewItems = reviewItems.toMutableList().also {
                            it[index] = item.copy(chosenPhone = phoneOption)
                          }
                        },
                      )
                      Text(phoneOption, style = MaterialTheme.typography.bodyMedium)
                    }
                  }
                } else if (item.chosenPhone.isNotBlank()) {
                  Text("Phone: ${item.chosenPhone}", style = MaterialTheme.typography.bodySmall)
                }

                if (item.contact.emails.size > 1) {
                  Text("Choose email:", style = MaterialTheme.typography.labelMedium)
                  item.contact.emails.forEach { emailOption ->
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSaving) {
                          reviewItems = reviewItems.toMutableList().also {
                            it[index] = item.copy(chosenEmail = emailOption)
                          }
                        },
                    ) {
                      RadioButton(
                        enabled = !isSaving,
                        selected = item.chosenEmail == emailOption,
                        onClick = {
                          reviewItems = reviewItems.toMutableList().also {
                            it[index] = item.copy(chosenEmail = emailOption)
                          }
                        },
                      )
                      Text(emailOption, style = MaterialTheme.typography.bodyMedium)
                    }
                  }
                } else if (item.chosenEmail.isNotBlank()) {
                  Text("Email: ${item.chosenEmail}", style = MaterialTheme.typography.bodySmall)
                }

                if (match != null) {
                  ImportDuplicateDecision(match, item.keepIfDuplicate, enabled = !isSaving) { keep ->
                    reviewItems = reviewItems.toMutableList().also {
                      it[index] = item.copy(keepIfDuplicate = keep)
                    }
                  }
                }
              }
            }
          }
        }

        if (saveFailed) {
          Text(
            "Could not save this import. Review choices are still here.",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.error,
          )
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = onBack,
            enabled = !isSaving,
            modifier = Modifier.weight(1f),
          ) {
            Text("Cancel")
          }
          Button(
            enabled = !isSaving,
            modifier = Modifier.weight(1f),
            onClick = {
              isSaving = true
              saveFailed = false
              scope.launch {
                try {
                  val toSave = approvedItems.mapIndexed { index, item ->
                    PersonProfile(
                      id = "pending-$index",
                      name = item.contact.displayName.trim(),
                      phone = item.chosenPhone.trim(),
                      email = item.chosenEmail.trim(),
                    )
                  }
                  val saved = repository.saveProfilesBatch(toSave)
                  onImported("${saved.size} saved, $skippedCount skipped.")
                } catch (cancelled: CancellationException) {
                  throw cancelled
                } catch (_: Exception) {
                  saveFailed = true
                } finally {
                  isSaving = false
                }
              }
            },
          ) {
            Text(if (approvedItems.isEmpty()) "Done" else "Save contacts")
          }
        }
      }
    }
  }
}

private fun computeBatchMatches(
  items: List<ReviewedContact>,
  existingProfiles: List<PersonProfile>,
): Map<Int, DuplicateMatch> {
  val matches = mutableMapOf<Int, DuplicateMatch>()
  val runningCandidates = existingProfiles.toMutableList()
  items.forEachIndexed { idx, item ->
    val match = DuplicateMatcher.findMatch(
      phone = item.chosenPhone,
      email = item.chosenEmail,
      candidates = runningCandidates,
    )
    if (match != null) {
      matches[idx] = match
    }
    if (match == null || item.keepIfDuplicate) {
      runningCandidates += PersonProfile(
        id = "batch-$idx",
        name = item.contact.displayName,
        phone = item.chosenPhone,
        email = item.chosenEmail,
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
      )
    }
  }
  return matches
}
