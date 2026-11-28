package com.nexus.prm.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.prm.data.DuplicateMatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportSourceSheet(
  onDismiss: () -> Unit,
  onContacts: () -> Unit,
  onCsv: () -> Unit,
  onVCard: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
      Text(
        "Import contacts",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleLarge,
      )
      ListItem(
        headlineContent = { Text("Android contacts") },
        supportingContent = { Text("Choose people saved on this device") },
        leadingContent = { Icon(Icons.Outlined.People, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onContacts),
      )
      ListItem(
        headlineContent = { Text("CSV roster") },
        supportingContent = { Text("Review rows from a roster file") },
        leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onCsv),
      )
      ListItem(
        headlineContent = { Text("vCard") },
        supportingContent = { Text("Open a single-contact .vcf file") },
        leadingContent = { Icon(Icons.Outlined.ContactPage, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onVCard),
      )
    }
  }
}

@Composable
internal fun ImportReviewSummary(
  ready: Int,
  skipped: Int? = null,
  duplicates: Int,
  invalid: Int = 0,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("Review import", style = MaterialTheme.typography.titleMedium)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text("$ready ready to save")
        skipped?.let { Text("$it skipped") }
        if (duplicates > 0) {
          Text(if (duplicates == 1) "1 possible duplicate" else "$duplicates possible duplicates")
        }
        if (invalid > 0) Text("$invalid invalid")
      }
    }
  }
}

@Composable
internal fun ImportDuplicateDecision(
  match: DuplicateMatch,
  keepSeparately: Boolean,
  enabled: Boolean,
  onDecision: (Boolean) -> Unit,
) {
  ImportDuplicateMessage(match)
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(
      selected = !keepSeparately,
      onClick = { onDecision(false) },
      enabled = enabled,
      label = { Text("Skip") },
    )
    FilterChip(
      selected = keepSeparately,
      onClick = { onDecision(true) },
      enabled = enabled,
      label = { Text("Keep separately") },
    )
  }
}

@Composable
internal fun ImportDuplicateMessage(match: DuplicateMatch) {
  Text(
    "Possible duplicate of ${match.existingProfile.name} (matching ${match.matchedField.label.lowercase()})",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
  )
}
