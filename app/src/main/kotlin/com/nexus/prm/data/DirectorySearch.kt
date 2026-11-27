package com.nexus.prm.data

object DirectorySearch {
  fun normalizePhoneDigits(raw: String): String = raw.filter { it.isDigit() }

  fun isPhoneLikeQuery(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.none { it.isDigit() }) return false
    return trimmed.all { it.isDigit() || it in PHONE_QUERY_CHARS }
  }

  private val PHONE_QUERY_CHARS = setOf('+', '-', '(', ')', ' ', '.')

  fun matchesQuery(
    profile: PersonProfile,
    query: String,
    groupNames: List<String> = emptyList(),
  ): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true

    val nonPhoneFields = buildList {
      add(profile.name)
      add(profile.email)
      add(profile.designation)
      add(profile.service)
      add(profile.cadre)
      add(profile.serviceBatch)
      add(profile.foundationCourseBatch)
      add(profile.organisation)
      add(profile.trainingGroup)
      add(profile.whereWeMet)
      add(profile.otherDetails)
      add(profile.categoryLabel().orEmpty())
      addAll(profile.tags)
      addAll(groupNames)
      for (posting in profile.postings) {
        add(posting.organisation)
        add(posting.designation)
        add(posting.location)
      }
      for (intro in profile.introductions) {
        add(intro.introducerName)
      }
    }

    if (profile.phone.contains(trimmed, ignoreCase = true) ||
      nonPhoneFields.any { it.contains(trimmed, ignoreCase = true) }
    ) {
      return true
    }

    if (isPhoneLikeQuery(trimmed) && profile.phone.isNotBlank()) {
      val queryDigits = normalizePhoneDigits(trimmed)
      val phoneDigits = normalizePhoneDigits(profile.phone)
      if (queryDigits.isNotEmpty() && phoneDigits.contains(queryDigits)) {
        return true
      }
    }

    val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
    if (words.size > 1) {
      return words.all { word ->
        nonPhoneFields.any { field -> field.contains(word, ignoreCase = true) }
      }
    }

    return false
  }

  private val WHITESPACE_REGEX = Regex("\\s+")

  fun filterAndSort(
    profiles: List<PersonProfile>,
    query: String,
    selectedCategory: PersonCategory?,
    selectedTrainingGroup: String?,
    favouritesOnly: Boolean,
    recentlyAddedOnly: Boolean,
    selectedGroupMemberIds: Set<String>? = null,
    selectedCategoryLabel: String? = null,
    allGroups: List<ContactGroup> = emptyList(),
  ): List<PersonProfile> {
    val groupNamesByProfileId = if (allGroups.isEmpty()) {
      emptyMap()
    } else {
      buildMap<String, MutableList<String>> {
        for (group in allGroups) {
          for (memberId in group.memberIds) {
            getOrPut(memberId) { mutableListOf() }.add(group.name)
          }
        }
      }
    }
    val effectiveCategoryLabel = selectedCategoryLabel?.trim()?.takeIf { it.isNotEmpty() }
      ?: selectedCategory?.label
    val filtered = profiles.filter { profile ->
      (effectiveCategoryLabel == null || profile.categoryLabel()?.equals(effectiveCategoryLabel, ignoreCase = true) == true) &&
        (selectedTrainingGroup == null || profile.trainingGroup.trim().equals(selectedTrainingGroup, ignoreCase = true)) &&
        (selectedGroupMemberIds == null || profile.id in selectedGroupMemberIds) &&
        (!favouritesOnly || profile.isFavourite) &&
        matchesQuery(profile, query, groupNamesByProfileId[profile.id].orEmpty())
    }

    return if (recentlyAddedOnly) {
      filtered.sortedWith(
        compareByDescending<PersonProfile> { it.createdAtEpochMs }
          .thenBy { it.name.lowercase() },
      )
    } else {
      filtered.sortedWith(
        compareByDescending<PersonProfile> { it.isFavourite }
          .thenBy { it.name.lowercase() }
          .thenByDescending { it.createdAtEpochMs },
      )
    }
  }

  fun distinctTrainingGroups(profiles: List<PersonProfile>): List<String> =
    profiles
      .map { it.trainingGroup.trim() }
      .filter { it.isNotEmpty() }
      .distinctBy { it.lowercase() }
      .sortedBy { it.lowercase() }

  fun distinctTags(profiles: List<PersonProfile>): List<String> =
    profiles
      .flatMap { it.tags }
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinctBy { it.lowercase() }
      .sortedBy { it.lowercase() }

  fun normalizeTags(
    rawTags: List<String>,
    existingProfiles: List<PersonProfile>,
  ): List<String> {
    val canonicalByLower = mutableMapOf<String, String>()
    for (profile in existingProfiles) {
      for (tag in profile.tags) {
        val trimmed = tag.trim()
        if (trimmed.isNotEmpty()) {
          canonicalByLower.putIfAbsent(trimmed.lowercase(), trimmed)
        }
      }
    }

    val result = mutableListOf<String>()
    val seenLower = mutableSetOf<String>()
    for (raw in rawTags) {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) continue
      val lower = trimmed.lowercase()
      if (seenLower.add(lower)) {
        result += canonicalByLower[lower] ?: trimmed
      }
    }
    return result
  }

  fun normalizeCategoryList(rawCategories: List<String>): List<String> {
    val result = mutableListOf<String>()
    val seenLower = mutableSetOf<String>()
    for (raw in rawCategories) {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) continue
      if (PersonCategory.fromLabel(trimmed) != null) continue
      val lower = trimmed.lowercase()
      if (seenLower.add(lower)) {
        result += trimmed
      }
    }
    return result
  }

  fun allCategoryOptions(customCategories: List<String>): List<String> =
    PersonCategory.entries.map { it.label } + normalizeCategoryList(customCategories)
}
