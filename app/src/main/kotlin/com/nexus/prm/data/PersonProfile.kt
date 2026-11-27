package com.nexus.prm.data

import kotlinx.serialization.Serializable

@Serializable
enum class PersonCategory(val label: String) {
  FELLOW_TRAINEE("Fellow trainee"),
  FACULTY("Faculty"),
  SENIOR_OFFICER("Senior officer"),
  OTHER("Other"),
  ;

  companion object {
    fun fromLabel(raw: String): PersonCategory? {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) return null
      return entries.firstOrNull { it.label.equals(trimmed, ignoreCase = true) }
    }
  }
}

@Serializable
data class PostingEntry(
  val id: String,
  val organisation: String = "",
  val designation: String = "",
  val location: String = "",
  val startDate: String = "",
  val endDate: String = "",
  val isCurrent: Boolean = false,
)

@Serializable
data class IntroductionRecord(
  val id: String,
  val introducerProfileId: String? = null,
  val introducerName: String,
  val date: String = "",
  val contextNote: String = "",
)

object PostingValidation {
  private val DD_MM_YYYY = Regex("^(\\d{2})-(\\d{2})-(\\d{4})$")
  private val YYYY_MM_DD = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

  data class SimpleDate(val year: Int, val month: Int, val day: Int) : Comparable<SimpleDate> {
    override fun compareTo(other: SimpleDate): Int =
      compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    fun formatDdMmYyyy(): String =
      "%02d-%02d-%04d".format(day, month, year)
  }

  fun daysInMonth(month: Int, year: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 31
  }

  fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

  fun parseDate(raw: String): SimpleDate? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val dmy = DD_MM_YYYY.matchEntire(trimmed)
    if (dmy != null) {
      val day = dmy.groupValues[1].toInt()
      val month = dmy.groupValues[2].toInt()
      val year = dmy.groupValues[3].toInt()
      return validateCalendarDate(year, month, day)
    }

    val ymd = YYYY_MM_DD.matchEntire(trimmed)
    if (ymd != null) {
      val year = ymd.groupValues[1].toInt()
      val month = ymd.groupValues[2].toInt()
      val day = ymd.groupValues[3].toInt()
      return validateCalendarDate(year, month, day)
    }

    return null
  }

  private fun validateCalendarDate(year: Int, month: Int, day: Int): SimpleDate? {
    if (year !in 1900..2100) return null
    if (month !in 1..12) return null
    val maxDay = daysInMonth(month, year)
    if (day !in 1..maxDay) return null
    return SimpleDate(year = year, month = month, day = day)
  }

  fun isValidDate(raw: String): Boolean =
    raw.trim().isEmpty() || parseDate(raw) != null

  fun isValidDateRange(startDate: String, endDate: String): Boolean {
    val startTrim = startDate.trim()
    val endTrim = endDate.trim()
    if (startTrim.isEmpty() || endTrim.isEmpty()) return true
    val parsedStart = parseDate(startTrim) ?: return false
    val parsedEnd = parseDate(endTrim) ?: return false
    return parsedEnd >= parsedStart
  }

  fun formatForDisplay(raw: String): String =
    parseDate(raw)?.formatDdMmYyyy() ?: raw.trim()
}

@Serializable
data class LabeledContactValue(
  val value: String,
  val label: String = DEFAULT_LABEL,
  val isPrimary: Boolean = false,
) {
  companion object {
    const val LABEL_PERSONAL = "Personal"
    const val LABEL_OFFICE = "Office"
    const val DEFAULT_LABEL = LABEL_PERSONAL
    val PRESET_LABELS = listOf(LABEL_PERSONAL, LABEL_OFFICE)
  }
}

@Serializable
data class PersonProfile(
  val id: String,
  val name: String,
  val phone: String = "",
  val phones: List<LabeledContactValue> = emptyList(),
  val category: PersonCategory? = null,
  val customCategory: String? = null,
  val whereWeMet: String = "",
  val email: String = "",
  val emails: List<LabeledContactValue> = emptyList(),
  val organisation: String = "",
  val designation: String = "",
  val service: String = "",
  val cadre: String = "",
  val serviceBatch: String = "",
  val foundationCourseBatch: String = "",
  val trainingGroup: String = "",
  val otherDetails: String = "",
  val tags: List<String> = emptyList(),
  val postings: List<PostingEntry> = emptyList(),
  val introductions: List<IntroductionRecord> = emptyList(),
  val photoFileName: String? = null,
  val isFavourite: Boolean = false,
  val createdAtEpochMs: Long = 0L,
  val updatedAtEpochMs: Long = 0L,
) {
  fun categoryLabel(): String? =
    customCategory?.trim()?.takeIf { it.isNotEmpty() } ?: category?.label

  fun resolvedPhones(): List<LabeledContactValue> =
    normalizeContactList(
      raw = phones,
      legacyFallback = phone,
      normalizer = DuplicateMatcher::normalizePhone,
    )

  fun resolvedEmails(): List<LabeledContactValue> =
    normalizeContactList(
      raw = emails,
      legacyFallback = email,
      normalizer = DuplicateMatcher::normalizeEmail,
    )

  fun primaryPhone(): String =
    resolvedPhones().firstOrNull { it.isPrimary }?.value
      ?: resolvedPhones().firstOrNull()?.value.orEmpty()

  fun primaryEmail(): String =
    resolvedEmails().firstOrNull { it.isPrimary }?.value
      ?: resolvedEmails().firstOrNull()?.value.orEmpty()

  fun allPhoneValues(): List<String> = resolvedPhones().map { it.value }

  fun allEmailValues(): List<String> = resolvedEmails().map { it.value }

  fun currentPosting(): PostingEntry? = postings.firstOrNull { it.isCurrent }

  fun migrateLegacyTrainingFieldsToOtherDetails(): PersonProfile {
    val fc = foundationCourseBatch.trim()
    val tg = trainingGroup.trim()
    if (fc.isEmpty() && tg.isEmpty()) return this

    val existingNotes = otherDetails.trim()
    val appended = buildList {
      if (fc.isNotEmpty()) {
        val line = "Foundation Course batch: $fc"
        if (!existingNotes.contains(line, ignoreCase = true)) add(line)
      }
      if (tg.isNotEmpty()) {
        val line = "Training group: $tg"
        if (!existingNotes.contains(line, ignoreCase = true)) add(line)
      }
    }
    val mergedOtherDetails = (listOf(existingNotes).filter { it.isNotEmpty() } + appended)
      .joinToString("\n")
    return copy(
      foundationCourseBatch = "",
      trainingGroup = "",
      otherDetails = mergedOtherDetails,
    )
  }

  fun withSyncedPostings(): PersonProfile {
    val normalizedPhones = resolvedPhones()
    val normalizedEmails = resolvedEmails()
    val syncedPhone = normalizedPhones.firstOrNull { it.isPrimary }?.value.orEmpty()
    val syncedEmail = normalizedEmails.firstOrNull { it.isPrimary }?.value.orEmpty()

    val ensuredPostings = if (postings.isEmpty() && (organisation.isNotBlank() || designation.isNotBlank())) {
      listOf(
        PostingEntry(
          id = "initial-$id",
          organisation = organisation.trim(),
          designation = designation.trim(),
          isCurrent = true,
        ),
      )
    } else {
      postings
    }
    val current = ensuredPostings.firstOrNull { it.isCurrent }
    return if (current != null) {
      copy(
        phone = syncedPhone,
        phones = normalizedPhones,
        email = syncedEmail,
        emails = normalizedEmails,
        postings = ensuredPostings,
        organisation = current.organisation,
        designation = current.designation,
      )
    } else {
      copy(
        phone = syncedPhone,
        phones = normalizedPhones,
        email = syncedEmail,
        emails = normalizedEmails,
        postings = ensuredPostings,
      )
    }
  }

  companion object {
    fun normalizeContactList(
      raw: List<LabeledContactValue>,
      legacyFallback: String = "",
      normalizer: (String) -> String,
    ): List<LabeledContactValue> {
      val source = if (raw.isEmpty() && legacyFallback.isNotBlank()) {
        listOf(
          LabeledContactValue(
            value = legacyFallback.trim(),
            label = LabeledContactValue.DEFAULT_LABEL,
            isPrimary = true,
          ),
        )
      } else {
        raw
      }

      val deduped = mutableListOf<LabeledContactValue>()
      val seenKeys = mutableMapOf<String, Int>()
      for (item in source) {
        val trimmedValue = item.value.trim()
        if (trimmedValue.isEmpty()) continue
        val trimmedLabel = item.label.trim().ifEmpty { LabeledContactValue.DEFAULT_LABEL }
        val key = normalizer(trimmedValue).ifEmpty { trimmedValue.lowercase() }
        val existingIdx = seenKeys[key]
        if (existingIdx == null) {
          seenKeys[key] = deduped.size
          deduped += LabeledContactValue(
            value = trimmedValue,
            label = trimmedLabel,
            isPrimary = item.isPrimary,
          )
        } else if (item.isPrimary && !deduped[existingIdx].isPrimary) {
          deduped[existingIdx] = deduped[existingIdx].copy(isPrimary = true)
        }
      }

      if (deduped.isEmpty()) return emptyList()
      val primaryIdx = deduped.indexOfFirst { it.isPrimary }.takeIf { it >= 0 } ?: 0
      return deduped.mapIndexed { idx, entry ->
        entry.copy(isPrimary = idx == primaryIdx)
      }
    }
  }
}

@Serializable
data class ContactGroup(
  val id: String,
  val name: String,
  val memberIds: List<String> = emptyList(),
  val createdAtEpochMs: Long = 0L,
)

@Serializable
data class OwnerCard(
  val name: String = "",
  val phone: String = "",
  val email: String = "",
  val organisation: String = "",
  val designation: String = "",
  val service: String = "",
  val cadre: String = "",
  val serviceBatch: String = "",
  val foundationCourseBatch: String = "",
  val trainingGroup: String = "",
  val shareName: Boolean = true,
  val sharePhone: Boolean = true,
  val shareEmail: Boolean = true,
  val shareOrganisation: Boolean = true,
  val shareDesignation: Boolean = true,
  val shareService: Boolean = true,
  val shareCadre: Boolean = true,
  val shareServiceBatch: Boolean = true,
  val shareFoundationCourseBatch: Boolean = true,
  val shareTrainingGroup: Boolean = true,
)
