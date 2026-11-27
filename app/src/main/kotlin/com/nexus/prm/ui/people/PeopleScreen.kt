package com.nexus.prm.ui.people

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.ContactGroup
import com.nexus.prm.data.PersonCategory
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.ui.components.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
  viewModel: PeopleViewModel,
  onAddClick: () -> Unit,
  onPersonClick: (String) -> Unit,
  onSettingsClick: () -> Unit,
  onImportContactsClick: () -> Unit,
  onImportCsvClick: () -> Unit,
  onManageGroupsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var moreMenuExpanded by remember { mutableStateOf(false) }

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
          Column {
            Text("People", fontWeight = FontWeight.Bold)
            if (uiState.totalCount > 0) {
              Text(
                text = "${uiState.visibleProfiles.size} of ${uiState.totalCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        actions = {
          Box {
            IconButton(onClick = { moreMenuExpanded = true }) {
              Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            DropdownMenu(
              expanded = moreMenuExpanded,
              onDismissRequest = { moreMenuExpanded = false },
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              DropdownMenuItem(
                text = { Text("Import from Android contacts") },
                onClick = {
                  moreMenuExpanded = false
                  onImportContactsClick()
                },
              )
              DropdownMenuItem(
                text = { Text("Import CSV roster") },
                onClick = {
                  moreMenuExpanded = false
                  onImportCsvClick()
                },
              )
              DropdownMenuItem(
                text = { Text("Manage groups") },
                onClick = {
                  moreMenuExpanded = false
                  onManageGroupsClick()
                },
              )
            }
          }
          IconButton(onClick = onSettingsClick) {
            Icon(
              imageVector = Icons.Outlined.Settings,
              contentDescription = "Settings",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onAddClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("Add", fontWeight = FontWeight.SemiBold) },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      OutlinedTextField(
        value = uiState.filter.query,
        onValueChange = viewModel::onQueryChange,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        placeholder = { Text("Search name, phone, email, role, location, group") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
          if (uiState.filter.query.isNotEmpty()) {
            IconButton(onClick = { viewModel.onQueryChange("") }) {
              Icon(Icons.Filled.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
      )

      FilterBar(
        filter = uiState.filter,
        trainingGroups = uiState.trainingGroups,
        reusableGroups = uiState.reusableGroups,
        onToggleFavourites = viewModel::onToggleFavouritesOnly,
        onToggleRecent = viewModel::onToggleRecentlyAdded,
        onSelectCategory = viewModel::onCategorySelected,
        onSelectGroup = viewModel::onTrainingGroupSelected,
        onSelectReusableGroup = viewModel::onReusableGroupSelected,
      )

      if (uiState.visibleProfiles.isEmpty()) {
        EmptyPeopleState(
          hasAnyProfiles = uiState.totalCount > 0,
          onAddClick = onAddClick,
          onClearFilters = viewModel::clearFilters,
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(uiState.visibleProfiles, key = { it.id }) { profile ->
            PersonListCard(
              profile = profile,
              loadPhotoBytes = viewModel::loadPhotoBytes,
              onClick = { onPersonClick(profile.id) },
              onToggleFavourite = { viewModel.toggleFavourite(profile.id) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FilterBar(
  filter: PeopleFilterState,
  trainingGroups: List<String>,
  reusableGroups: List<ContactGroup>,
  onToggleFavourites: () -> Unit,
  onToggleRecent: () -> Unit,
  onSelectCategory: (PersonCategory?) -> Unit,
  onSelectGroup: (String?) -> Unit,
  onSelectReusableGroup: (String?) -> Unit,
) {
  var categoryMenuExpanded by remember { mutableStateOf(false) }
  var groupMenuExpanded by remember { mutableStateOf(false) }
  var reusableGroupMenuExpanded by remember { mutableStateOf(false) }
  val chipColors = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
  )

  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      FilterChip(
        selected = filter.favouritesOnly,
        onClick = onToggleFavourites,
        colors = chipColors,
        label = { Text("Favourites") },
        leadingIcon = {
          Icon(
            imageVector = if (filter.favouritesOnly) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = null,
          )
        },
      )
    }

    item {
      FilterChip(
        selected = filter.recentlyAddedOnly,
        onClick = onToggleRecent,
        colors = chipColors,
        label = { Text("Recently added") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
          )
        },
      )
    }

    item {
      Box {
        FilterChip(
          selected = filter.selectedCategory != null,
          onClick = { categoryMenuExpanded = true },
          colors = chipColors,
          label = { Text(filter.selectedCategory?.label ?: "Category") },
          leadingIcon = if (filter.selectedCategory != null) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
          } else null,
          trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(
          expanded = categoryMenuExpanded,
          onDismissRequest = { categoryMenuExpanded = false },
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          DropdownMenuItem(
            text = { Text("All categories") },
            onClick = {
              onSelectCategory(null)
              categoryMenuExpanded = false
            },
          )
          PersonCategory.entries.forEach { category ->
            DropdownMenuItem(
              text = { Text(category.label) },
              trailingIcon = if (filter.selectedCategory == category) {
                { Icon(Icons.Filled.Check, contentDescription = null) }
              } else null,
              onClick = {
                onSelectCategory(category)
                categoryMenuExpanded = false
              },
            )
          }
        }
      }
    }

    item {
      Box {
        FilterChip(
          selected = filter.selectedTrainingGroup != null,
          onClick = { groupMenuExpanded = true },
          enabled = trainingGroups.isNotEmpty() || filter.selectedTrainingGroup != null,
          colors = chipColors,
          label = { Text(filter.selectedTrainingGroup ?: "Training group") },
          leadingIcon = if (filter.selectedTrainingGroup != null) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
          } else null,
          trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(
          expanded = groupMenuExpanded,
          onDismissRequest = { groupMenuExpanded = false },
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          DropdownMenuItem(
            text = { Text("All groups") },
            onClick = {
              onSelectGroup(null)
              groupMenuExpanded = false
            },
          )
          trainingGroups.forEach { group ->
            DropdownMenuItem(
              text = { Text(group) },
              trailingIcon = if (filter.selectedTrainingGroup?.equals(group, ignoreCase = true) == true) {
                { Icon(Icons.Filled.Check, contentDescription = null) }
              } else null,
              onClick = {
                onSelectGroup(group)
                groupMenuExpanded = false
              },
            )
          }
        }
      }
    }

    if (reusableGroups.isNotEmpty() || filter.selectedReusableGroupId != null) {
      item {
        val activeReusableGroup = reusableGroups.firstOrNull { it.id == filter.selectedReusableGroupId }
        Box {
          FilterChip(
            selected = activeReusableGroup != null,
            onClick = { reusableGroupMenuExpanded = true },
            colors = chipColors,
            label = { Text(activeReusableGroup?.name ?: "Group") },
            leadingIcon = if (activeReusableGroup != null) {
              { Icon(Icons.Filled.Check, contentDescription = null) }
            } else null,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
          )
          DropdownMenu(
            expanded = reusableGroupMenuExpanded,
            onDismissRequest = { reusableGroupMenuExpanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            DropdownMenuItem(
              text = { Text("All groups") },
              onClick = {
                onSelectReusableGroup(null)
                reusableGroupMenuExpanded = false
              },
            )
            reusableGroups.forEach { group ->
              DropdownMenuItem(
                text = { Text(group.name) },
                trailingIcon = if (filter.selectedReusableGroupId == group.id) {
                  { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
                onClick = {
                  onSelectReusableGroup(group.id)
                  reusableGroupMenuExpanded = false
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PersonListCard(
  profile: PersonProfile,
  loadPhotoBytes: suspend (String) -> ByteArray?,
  onClick: () -> Unit,
  onToggleFavourite: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ProfileAvatar(
        name = profile.name,
        photoFileName = profile.photoFileName,
        loadPhotoBytes = loadPhotoBytes,
        size = 52.dp,
      )
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = profile.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val primaryMeta = listOfNotNull(
          profile.category?.label,
          profile.trainingGroup.takeIf { it.isNotBlank() },
          profile.service.takeIf { it.isNotBlank() },
        ).joinToString(" Â· ")
        if (primaryMeta.isNotBlank()) {
          Text(
            text = primaryMeta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        val secondaryMeta = profile.whereWeMet.ifBlank { profile.phone }
        if (secondaryMeta.isNotBlank()) {
          Text(
            text = secondaryMeta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (profile.tags.isNotEmpty()) {
          Text(
            text = profile.tags.joinToString(" · ") { "#$it" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      IconButton(onClick = onToggleFavourite) {
        Icon(
          imageVector = if (profile.isFavourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
          contentDescription = if (profile.isFavourite) "Remove from favourites" else "Add to favourites",
          tint = if (profile.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun EmptyPeopleState(
  hasAnyProfiles: Boolean,
  onAddClick: () -> Unit,
  onClearFilters: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = if (hasAnyProfiles) "No matching profiles" else "Your directory is empty",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = if (hasAnyProfiles) {
          "Try another search term or clear active filters."
        } else {
          "Tap Add to save someone you just met during the Foundation Course."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(16.dp))
      if (hasAnyProfiles) {
        OutlinedButton(onClick = onClearFilters) {
          Text("Clear filters")
        }
      } else {
        Button(onClick = onAddClick) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Add person")
        }
      }
    }
  }
}
