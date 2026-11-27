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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.ContactGroup
import com.nexus.prm.data.DirectoryRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
  repository: DirectoryRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val groups by repository.groups.collectAsStateWithLifecycle()
  val profiles by repository.profiles.collectAsStateWithLifecycle()

  var showCreateDialog by remember { mutableStateOf(false) }
  var renamingGroup by remember { mutableStateOf<ContactGroup?>(null) }
  var deletingGroup by remember { mutableStateOf<ContactGroup?>(null) }
  var editingMembersGroup by remember { mutableStateOf<ContactGroup?>(null) }
  var errorText by remember { mutableStateOf<String?>(null) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        title = { Text("Groups", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
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
                Column(modifier = Modifier.weight(1f)) {
                  Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = "${group.memberIds.size} members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { editingMembersGroup = group }) {
                  Icon(Icons.Outlined.GroupAdd, contentDescription = "Edit members")
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

  editingMembersGroup?.let { group ->
    var selectedIds by remember(group.id) { mutableStateOf(group.memberIds.toSet()) }
    AlertDialog(
      onDismissRequest = { editingMembersGroup = null },
      title = { Text("Members: ${group.name}") },
      text = {
        if (profiles.isEmpty()) {
          Text("No people in your directory yet.")
        } else {
          LazyColumn(modifier = Modifier.height(320.dp)) {
            items(profiles, key = { it.id }) { person ->
              val checked = person.id in selectedIds
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    selectedIds = if (checked) selectedIds - person.id else selectedIds + person.id
                  }
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = checked,
                  onCheckedChange = { isChecked ->
                    selectedIds = if (isChecked) selectedIds + person.id else selectedIds - person.id
                  },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(person.name, style = MaterialTheme.typography.bodyLarge)
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            editingMembersGroup = null
            scope.launch { repository.setGroupMembers(group.id, selectedIds.toList()) }
          },
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { editingMembersGroup = null }) { Text("Cancel") }
      },
    )
  }
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
