package com.nexus.prm.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.prm.ui.theme.NexusFrostedStyle

@Composable
internal fun SettingsScreen(
  onBack: () -> Unit,
  onOpenBackups: () -> Unit,
  onOpenAppearance: () -> Unit,
  onOpenCallerId: () -> Unit,
  onOpenOwnerCard: () -> Unit,
) {
  SettingsPage(title = "Settings", onBack = onBack) {
    SettingsLink("Backups", "Backup, restore, and CSV export", Icons.Outlined.Backup, onOpenBackups)
    SettingsLink("Appearance", "Theme and accent colours", Icons.Outlined.Palette, onOpenAppearance)
    SettingsLink("Caller ID", "Incoming caller cards and permissions", Icons.Outlined.Phone, onOpenCallerId)
    SettingsLink("My contact card", "Share your own contact details", Icons.Outlined.ContactPage, onOpenOwnerCard)
  }
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
