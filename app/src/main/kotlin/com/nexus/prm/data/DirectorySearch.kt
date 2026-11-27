package com.nexus.prm.data

object DirectorySearch {
  fun normalizePhoneDigits(raw: String): String = raw.filter { it.isDigit() }

  fun isPhoneLikeQuery(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.none { it.isDigit() }) return false
    return trimmed.all { it.isDigit() || it in PHONE_QUERY_CHARS }
  }

  private val PHONE_QUERY_CHARS = setOf('+', '-', '(', ')', ' ', '.')

  fun matchesQuery(profile: PersonProfile, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true

    val textFields = listOf(
      profile.name,
      profile.phone,
      profile.email,
      profile.designation,
      profile.currentPosting()?.location.orEmpty(),
      profile.service,
      profile.cadre,
      profile.serviceBatch,
      profile.foundationCourseBatch,
      profile.organisation,
      profile.trainingGroup,
      profile.whereWeMet,
    )
    if (textFields.any { it.contains(trimmed, ignoreCase = true) }) {
      return true
    }

    if (profile.tags.any { it.contains(trimmed, ignoreCase = true) }) {
      return true
    }

    if (isPhoneLikeQuery(trimmed) && profile.phone.isNotBlank()) {
      val queryDigits = normalizePhoneDigits(trimmed)
      val phoneDigits = normalizePhoneDigits(profile.phone)
      if (queryDigits.isNotEmpty() && phoneDigits.contains(queryDigits)) {
        return true
      }
    }

    return false
  }

  fun filterAndSort(
    profiles: List<PersonProfile>,
    query: String,
    selectedCategory: PersonCategory?,
    selectedTrainingGroup: String?,
    favouritesOnly: Boolean,
    recentlyAddedOnly: Boolean,
    selectedGroupMemberIds: Set<String>? = null,
  ): List<PersonProfile> {
    val filtered = profiles.filter { profile ->
      (selectedCategory == null || profile.category == selectedCategory) &&
        (selectedTrainingGroup == null || profile.trainingGroup.trim().equals(selectedTrainingGroup, ignoreCase = true)) &&
        (selectedGroupMemberIds == null || profile.id in selectedGroupMemberIds) &&
        (!favouritesOnly || profile.isFavourite) &&
        matchesQuery(profile, query)
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
}
