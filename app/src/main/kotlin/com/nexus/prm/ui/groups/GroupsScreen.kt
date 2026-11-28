package com.nexus.prm.ui.groups

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.ContactGroup
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.ui.theme.NexusFrostedStyle
import java.io.IOException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
  repository: DirectoryRepository,
  onOpenGroup: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val groups by repository.groups.collectAsStateWithLifecycle()

  var showCreateDialog by remember { mutableStateOf(false) }
  var renamingGroup by remember { mutableStateOf<ContactGroup?>(null) }
  var deletingGroup by remember { mutableStateOf<ContactGroup?>(null) }
  var errorText by remember { mutableStateOf<String?>(null) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = NexusFrostedStyle.topAppBarColors(),
        title = { Text("Groups", fontWeight = FontWeight.SemiBold) },
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        modifier = Modifier.semantics { contentDescription = "New group" },
        onClick = {
          errorText = null
          showCreateDialog = true
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("New group") },
      )
    },
  ) { innerPadding ->
    if (groups.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("No reusable groups yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text(
            text = "Create named groups (such as Mess Table 4 or Syndicate) without replacing anyone's training group.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(groups, key = { it.id }) { group ->
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
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(
                  modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "View members") { onOpenGroup(group.id) }
                    .padding(vertical = 8.dp),
                ) {
                  Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = if (group.memberIds.size == 1) "1 member" else "${group.memberIds.size} members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = {
                    errorText = null
                    renamingGroup = group
                  },
                ) {
                  Icon(Icons.Outlined.Edit, contentDescription = "Rename group")
                }
                IconButton(onClick = { deletingGroup = group }) {
                  Icon(Icons.Outlined.Delete, contentDescription = "Delete group")
                }
              }
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    GroupNameDialog(
      title = "Create group",
      initialName = "",
      errorText = errorText,
      onDismiss = { showCreateDialog = false },
      onConfirm = { name ->
        scope.launch {
          runCatching { repository.createGroup(name) }
            .onSuccess { showCreateDialog = false }
            .onFailure { errorText = it.message ?: "Could not create group" }
        }
      },
    )
  }

  renamingGroup?.let { group ->
    GroupNameDialog(
      title = "Rename group",
      initialName = group.name,
      errorText = errorText,
      onDismiss = { renamingGroup = null },
      onConfirm = { newName ->
        scope.launch {
          runCatching { repository.renameGroup(group.id, newName) }
            .onSuccess { renamingGroup = null }
            .onFailure { errorText = it.message ?: "Could not rename group" }
        }
      },
    )
  }

  deletingGroup?.let { group ->
    AlertDialog(
      onDismissRequest = { deletingGroup = null },
      title = { Text("Delete ${group.name}?") },
      text = {
        Text("This removes the group and its memberships, while keeping all people in your directory.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            deletingGroup = null
            scope.launch { repository.deleteGroup(group.id) }
          },
        ) {
          Text("Delete", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { deletingGroup = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
internal fun ManageGroupMembersDialog(
  repository: DirectoryRepository,
  group: ContactGroup,
  onDismiss: () -> Unit,
) {
  val profiles by repository.profiles.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  var selectedIds by rememberSaveable(group.id) { mutableStateOf(ArrayList(group.memberIds)) }
  var query by rememberSaveable(group.id) { mutableStateOf("") }
  var saving by remember { mutableStateOf(false) }
  var saveFailed by remember { mutableStateOf(false) }
  val visibleProfiles = remember(profiles, query) {
    profiles.filter { it.name.contains(query.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase() }
  }

  AlertDialog(
    onDismissRequest = { if (!saving) onDismiss() },
    title = { Text("Manage members") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          label = { Text("Find people") },
          enabled = !saving,
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text("${selectedIds.size} selected")
        if (visibleProfiles.isEmpty()) {
          Text(if (profiles.isEmpty()) "Your directory is empty." else "No matching people.")
        } else {
          LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
            items(visibleProfiles, key = { it.id }) { person ->
              val checked = person.id in selectedIds
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .toggleable(value = checked, enabled = !saving, role = Role.Checkbox) { isChecked ->
                    selectedIds = ArrayList(if (isChecked) selectedIds + person.id else selectedIds - person.id)
                  },
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = !saving)
                Spacer(modifier = Modifier.width(12.dp))
                Text(person.name, style = MaterialTheme.typography.bodyLarge)
              }
            }
          }
        }
        if (saveFailed) {
          Text("Could not save members. Try again.", color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        enabled = !saving,
        onClick = {
          saving = true
          saveFailed = false
          scope.launch {
            try {
              repository.setGroupMembers(group.id, selectedIds)
              onDismiss()
            } catch (_: IOException) {
              saveFailed = true
            } finally {
              saving = false
            }
          }
        },
      ) { Text("Save") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
    },
  )
}

@Composable
private fun GroupNameDialog(
  title: String,
  initialName: String,
  errorText: String?,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Group name") },
        singleLine = true,
        isError = errorText != null,
        supportingText = errorText?.let { msg -> { Text(msg) } },
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      Button(onClick = { onConfirm(name) }) { Text("Save") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
