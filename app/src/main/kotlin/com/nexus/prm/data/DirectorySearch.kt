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

    val allPhones = profile.allPhoneValues()
    val nonPhoneFields = buildList {
      add(profile.name)
      addAll(profile.allEmailValues())
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

    if (allPhones.any { it.contains(trimmed, ignoreCase = true) } ||
      nonPhoneFields.any { it.contains(trimmed, ignoreCase = true) }
    ) {
      return true
    }

    if (isPhoneLikeQuery(trimmed) && allPhones.isNotEmpty()) {
      val queryDigits = normalizePhoneDigits(trimmed)
      if (queryDigits.isNotEmpty() &&
        allPhones.any { normalizePhoneDigits(it).contains(queryDigits) }
      ) {
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

  fun explainMatch(
    profile: PersonProfile,
    query: String,
    groupNames: List<String> = emptyList(),
  ): String? {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return null

    if (profile.name.contains(trimmed, ignoreCase = true)) return null
    val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
    if (words.size > 1 && words.all { profile.name.contains(it, ignoreCase = true) }) {
      return null
    }

    val tokens = if (words.size > 1) listOf(trimmed) + words else listOf(trimmed)
    val isPhoneQuery = isPhoneLikeQuery(trimmed)
    val queryDigits = if (isPhoneQuery) normalizePhoneDigits(trimmed) else ""

    for (token in tokens) {
      // 1. Past postings (location, designation, organisation)
      for (posting in profile.postings) {
        if (posting.isCurrent) continue
        val matchedValue = firstMatchingNonBlank(
          token,
          posting.location,
          posting.designation,
          posting.organisation,
        )
        if (matchedValue != null) {
          return "Past posting \u00B7 ${matchedValue.take(28)}"
        }
      }

      // 2. Introductions
      for (intro in profile.introductions) {
        val introName = intro.introducerName.trim()
        if (introName.isNotEmpty() && introName.contains(token, ignoreCase = true)) {
          return "Introduced by ${introName.take(28)}"
        }
      }

      // 3. Phone numbers (never expose full phone number in the hint)
      val matchedPhone = profile.resolvedPhones().firstOrNull { entry ->
        entry.value.contains(token, ignoreCase = true) ||
          (isPhoneQuery && queryDigits.isNotEmpty() && normalizePhoneDigits(entry.value).contains(queryDigits))
      }
      if (matchedPhone != null) {
        return "Match \u00B7 ${matchedPhone.label} number"
      }

      // 4. Emails
      val matchedEmail = profile.resolvedEmails().firstOrNull { entry ->
        entry.value.contains(token, ignoreCase = true)
      }
      if (matchedEmail != null) {
        return "Match \u00B7 ${matchedEmail.label} email"
      }

      // 5. Current posting
      for (posting in profile.postings) {
        if (!posting.isCurrent) continue
        val matchedValue = firstMatchingNonBlank(
          token,
          posting.location,
          posting.designation,
          posting.organisation,
        )
        if (matchedValue != null) {
          return "Current posting \u00B7 ${matchedValue.take(28)}"
        }
      }

      // 6. Groups
      val matchedGroup = groupNames.firstOrNull { it.contains(token, ignoreCase = true) }
      if (matchedGroup != null) {
        return "Group \u00B7 ${matchedGroup.trim().take(28)}"
      }

      // 7. Tags
      val matchedTag = profile.tags.firstOrNull { it.contains(token, ignoreCase = true) }
      if (matchedTag != null) {
        return "Tag \u00B7 #${matchedTag.trim().take(24)}"
      }

      // 8. Structured directory metadata
      val metaCandidates = listOf(
        "Service" to profile.service,
        "Cadre" to profile.cadre,
        "Batch" to profile.serviceBatch,
        "FC batch" to profile.foundationCourseBatch,
        "Training group" to profile.trainingGroup,
        "Category" to profile.categoryLabel().orEmpty(),
        "Role" to profile.designation,
        "Org" to profile.organisation,
      )
      for ((label, raw) in metaCandidates) {
        val cleaned = raw.trim()
        if (cleaned.isNotEmpty() && cleaned.contains(token, ignoreCase = true)) {
          return "$label \u00B7 ${cleaned.take(28)}"
        }
      }

      // 9. Private notes (never expose note contents)
      if (profile.whereWeMet.contains(token, ignoreCase = true)) {
        return "Match \u00B7 Where we met"
      }
      if (profile.otherDetails.contains(token, ignoreCase = true)) {
        return "Match \u00B7 Other details"
      }
    }

    return null
  }

  private fun firstMatchingNonBlank(token: String, vararg values: String): String? =
    values.firstOrNull { raw ->
      val cleaned = raw.trim()
      cleaned.isNotEmpty() && cleaned.contains(token, ignoreCase = true)
    }?.trim()

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
