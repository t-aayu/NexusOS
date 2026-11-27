package com.nexus.prm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.ui.people.personCardSubtitle

internal fun filterContactPickerProfiles(
  profiles: List<PersonProfile>,
  query: String,
): List<PersonProfile> = DirectorySearch.filterAndSort(
  profiles = profiles,
  query = query,
  selectedCategory = null,
  selectedTrainingGroup = null,
  favouritesOnly = false,
  recentlyAddedOnly = false,
)

@Composable
fun SearchableContactPickerDialog(
  profiles: List<PersonProfile>,
  selectedProfileId: String?,
  onSelectProfile: (PersonProfile) -> Unit,
  onSelectManual: () -> Unit,
  onDismiss: () -> Unit,
  title: String = "Choose a Nexus profile",
) {
  var searchQuery by remember { mutableStateOf("") }
  val filteredProfiles = remember(profiles, searchQuery) {
    filterContactPickerProfiles(profiles, searchQuery)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 24.dp)
        .imePadding(),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
          )

          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search people") },
            leadingIcon = {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
              )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
              {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                  )
                }
              }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 120.dp, max = 320.dp)
              .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (filteredProfiles.isEmpty()) {
              item {
                Text(
                  text = "No matching contacts",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(vertical = 16.dp),
                )
              }
            } else {
              items(filteredProfiles, key = { it.id }) { candidate ->
                val isSelected = candidate.id == selectedProfileId
                val subtitle = personCardSubtitle(candidate)
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectProfile(candidate) },
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.outlineVariant
                    },
                  ),
                  colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                  ),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(
                      modifier = Modifier.weight(1f),
                      verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                      Text(
                        text = candidate.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      if (subtitle.isNotBlank()) {
                        Text(
                          text = subtitle,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                    }
                    if (isSelected) {
                      Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(onClick = onSelectManual) {
              Text("Type name manually")
            }
            TextButton(onClick = onDismiss) {
              Text("Cancel")
            }
          }
        }
      }
    }
  }
}
