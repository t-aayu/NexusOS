package com.nexus.prm.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.CountryOption
import com.nexus.prm.data.CountryPreferences
import com.nexus.prm.ui.theme.NexusFrostedStyle

@Composable
internal fun SettingsScreen(
  onBack: () -> Unit,
  onOpenBackups: () -> Unit,
  onOpenAppearance: () -> Unit,
  onOpenCallerId: () -> Unit,
  onOpenOwnerCard: () -> Unit,
) {
  val context = LocalContext.current
  val selectedCountry by CountryPreferences.countryFlow(context).collectAsStateWithLifecycle()
  var showCountryPicker by remember { mutableStateOf(false) }

  SettingsPage(title = "Settings", onBack = onBack) {
    SettingsLink("Backups", "Backup, restore, and CSV export", Icons.Outlined.Backup, onOpenBackups)
    SettingsLink("Appearance", "Theme and accent colours", Icons.Outlined.Palette, onOpenAppearance)
    SettingsLink("Caller ID", "Incoming caller cards and permissions", Icons.Outlined.Phone, onOpenCallerId)
    SettingsLink("My contact card", "Share your own contact details", Icons.Outlined.ContactPage, onOpenOwnerCard)
    SettingsLink(
      title = "Default country",
      detail = selectedCountry?.let { "${it.displayLabel} · Used for WhatsApp on local numbers" }
        ?: "Not set · Requires + or 00 for WhatsApp",
      icon = Icons.Outlined.Public,
      onClick = { showCountryPicker = true },
    )
  }

  if (showCountryPicker) {
    DefaultCountryPickerDialog(
      selected = selectedCountry,
      onDismiss = { showCountryPicker = false },
      onSelect = { option ->
        CountryPreferences.setCountry(context, option)
        showCountryPicker = false
      },
    )
  }
}

@Composable
private fun DefaultCountryPickerDialog(
  selected: CountryOption?,
  onDismiss: () -> Unit,
  onSelect: (CountryOption?) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered = remember(query) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      CountryPreferences.countries
    } else {
      CountryPreferences.countries.filter { option ->
        option.name.contains(trimmed, ignoreCase = true) ||
          option.dialCode.contains(trimmed) ||
          option.isoCode.contains(trimmed, ignoreCase = true)
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Default country") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Applied to phone numbers saved without + or 00 when opening WhatsApp.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          label = { Text("Search country or code") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        ) {
          item(key = "none") {
            ListItem(
              headlineContent = { Text("None (require + or 00)") },
              trailingContent = if (selected == null) {
                { Icon(Icons.Filled.Check, contentDescription = "Selected") }
              } else null,
              modifier = Modifier.clickable { onSelect(null) },
            )
          }
          items(filtered, key = { it.isoCode }) { option ->
            ListItem(
              headlineContent = { Text(option.name) },
              supportingContent = { Text("+${option.dialCode}") },
              trailingContent = if (selected?.isoCode == option.isoCode) {
                { Icon(Icons.Filled.Check, contentDescription = "Selected") }
              } else null,
              modifier = Modifier.clickable { onSelect(option) },
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}

@Composable
private fun SettingsLink(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(detail) },
    leadingContent = { Icon(icon, contentDescription = null) },
    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
    modifier = Modifier.clickable(onClick = onClick),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  busy: Boolean = false,
  statusMessage: String? = null,
  errorMessage: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  BackHandler(enabled = busy) {}
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = NexusFrostedStyle.topAppBarColors(),
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack, enabled = !busy) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding)
        .verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      statusMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics {
          liveRegion = LiveRegionMode.Polite
        })
      }
      errorMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics {
          liveRegion = LiveRegionMode.Polite
        })
      }
      content()
    }
  }
}
