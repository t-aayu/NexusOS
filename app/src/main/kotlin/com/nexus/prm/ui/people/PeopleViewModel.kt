package com.nexus.prm.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.prm.data.ContactGroup
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.PersonCategory
import com.nexus.prm.data.PersonProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PeopleFilterState(
  val query: String = "",
  val selectedCategoryLabel: String? = null,
  val selectedTrainingGroup: String? = null,
  val selectedReusableGroupId: String? = null,
  val favouritesOnly: Boolean = false,
  val recentlyAddedOnly: Boolean = false,
)

data class PeopleUiState(
  val totalCount: Int = 0,
  val visibleProfiles: List<PersonProfile> = emptyList(),
  val availableCategories: List<String> = DirectorySearch.allCategoryOptions(emptyList()),
  val trainingGroups: List<String> = emptyList(),
  val reusableGroups: List<ContactGroup> = emptyList(),
  val filter: PeopleFilterState = PeopleFilterState(),
)

class PeopleViewModel(
  private val repository: DirectoryRepository,
) : ViewModel() {

  private val filterState = MutableStateFlow(PeopleFilterState())

  val uiState: StateFlow<PeopleUiState> =
    combine(
      repository.profiles,
      repository.groups,
      repository.customCategories,
      filterState,
    ) { allProfiles, allGroups, customCategories, filter ->
      val availableCategories = DirectorySearch.allCategoryOptions(
        customCategories + allProfiles.mapNotNull { it.customCategory },
      )
      val activeCategory = filter.selectedCategoryLabel?.takeIf { selected ->
        availableCategories.any { it.equals(selected, ignoreCase = true) }
      }
      val trainingGroups = DirectorySearch.distinctTrainingGroups(allProfiles)
      val activeTrainingGroup = filter.selectedTrainingGroup?.takeIf { selected ->
        trainingGroups.any { it.equals(selected, ignoreCase = true) }
      }
      val activeReusableGroup = allGroups.firstOrNull { it.id == filter.selectedReusableGroupId }
      val normalizedFilter = filter.copy(
        selectedCategoryLabel = activeCategory,
        selectedTrainingGroup = activeTrainingGroup,
        selectedReusableGroupId = activeReusableGroup?.id,
      )

      PeopleUiState(
        totalCount = allProfiles.size,
        visibleProfiles = DirectorySearch.filterAndSort(
          profiles = allProfiles,
          query = normalizedFilter.query,
          selectedCategory = null,
          selectedTrainingGroup = normalizedFilter.selectedTrainingGroup,
          favouritesOnly = normalizedFilter.favouritesOnly,
          recentlyAddedOnly = normalizedFilter.recentlyAddedOnly,
          selectedGroupMemberIds = activeReusableGroup?.memberIds?.toSet(),
          selectedCategoryLabel = normalizedFilter.selectedCategoryLabel,
        ),
        availableCategories = availableCategories,
        trainingGroups = trainingGroups,
        reusableGroups = allGroups,
        filter = normalizedFilter,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = PeopleUiState(
        totalCount = repository.profiles.value.size,
        visibleProfiles = DirectorySearch.filterAndSort(
          profiles = repository.profiles.value,
          query = "",
          selectedCategory = null,
          selectedTrainingGroup = null,
          favouritesOnly = false,
          recentlyAddedOnly = false,
        ),
        availableCategories = DirectorySearch.allCategoryOptions(
          repository.customCategories.value + repository.profiles.value.mapNotNull { it.customCategory },
        ),
        trainingGroups = DirectorySearch.distinctTrainingGroups(repository.profiles.value),
        reusableGroups = repository.groups.value,
      ),
    )

  fun onQueryChange(query: String) {
    filterState.value = filterState.value.copy(query = query)
  }

  fun onCategorySelected(categoryLabel: String?) {
    filterState.value = filterState.value.copy(selectedCategoryLabel = categoryLabel)
  }

  fun onTrainingGroupSelected(group: String?) {
    filterState.value = filterState.value.copy(selectedTrainingGroup = group)
  }

  fun onReusableGroupSelected(groupId: String?) {
    filterState.value = filterState.value.copy(selectedReusableGroupId = groupId)
  }

  fun onToggleFavouritesOnly() {
    filterState.value = filterState.value.copy(favouritesOnly = !filterState.value.favouritesOnly)
  }

  fun onToggleRecentlyAdded() {
    filterState.value = filterState.value.copy(recentlyAddedOnly = !filterState.value.recentlyAddedOnly)
  }

  fun clearFilters() {
    filterState.value = PeopleFilterState()
  }

  fun toggleFavourite(profileId: String) {
    viewModelScope.launch {
      repository.toggleFavourite(profileId)
    }
  }

  suspend fun loadPhotoBytes(photoFileName: String): ByteArray? =
    repository.loadPhotoBytes(photoFileName)
}
