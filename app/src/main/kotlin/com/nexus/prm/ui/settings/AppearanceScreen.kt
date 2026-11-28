package com.nexus.prm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.ui.theme.AccentChoice
import com.nexus.prm.ui.theme.NexusFrostedStyle
import com.nexus.prm.ui.theme.ThemeMode
import com.nexus.prm.ui.theme.ThemePreferences
import com.nexus.prm.ui.theme.isEffectiveDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  SettingsPage(title = "Appearance", onBack = onBack) {
    val appearance by ThemePreferences.settingsFlow(context).collectAsStateWithLifecycle()
    val dynamicColorSupported = remember { ThemePreferences.isDynamicColorSupported() }
    val effectiveDark = isEffectiveDark(appearance.themeMode, isSystemInDarkTheme())
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Theme",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            ThemeMode.entries.forEach { mode ->
              val selected = appearance.themeMode == mode
              FilterChip(
                selected = selected,
                onClick = { ThemePreferences.setThemeMode(context, mode) },
                leadingIcon = if (selected) {
                  { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
                label = { Text(mode.label) },
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Accent colour",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            val presetChoices = AccentChoice.entries.filter { it != AccentChoice.WALLPAPER }
            presetChoices.forEach { choice ->
              val selected = appearance.accentChoice == choice
              val swatchColor = androidx.compose.ui.graphics.Color(
                if (effectiveDark) choice.previewDarkHex else choice.previewLightHex,
              )
              FilterChip(
                selected = selected,
                onClick = { ThemePreferences.setAccentChoice(context, choice) },
                leadingIcon = {
                  Box(
                    modifier = Modifier
                      .size(18.dp)
                      .background(swatchColor, CircleShape),
                    contentAlignment = Alignment.Center,
                  ) {
                    if (selected) {
                      Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (effectiveDark) {
                          androidx.compose.ui.graphics.Color(0xFF10151D)
                        } else {
                          androidx.compose.ui.graphics.Color.White
                        },
                        modifier = Modifier.size(12.dp),
                      )
                    }
                  }
                },
                label = { Text(choice.label) },
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Use wallpaper colours",
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = if (dynamicColorSupported) {
                "Override preset accent with your wallpaper palette."
              } else {
                "Wallpaper colours require Android 12 or later."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = appearance.accentChoice == AccentChoice.WALLPAPER && dynamicColorSupported,
            enabled = dynamicColorSupported,
            onCheckedChange = { enabled ->
              ThemePreferences.setAccentChoice(
                context,
                if (enabled) AccentChoice.WALLPAPER else AccentChoice.SLATE_BLUE,
              )
            },
          )
        }
      }
  }
  }
}
