package com.nexus.prm.data

import kotlinx.serialization.Serializable

@Serializable
enum class PersonCategory(val label: String) {
  FELLOW_TRAINEE("Fellow trainee"),
  FACULTY("Faculty"),
  SENIOR_OFFICER("Senior officer"),
  OTHER("Other"),
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
data class PersonProfile(
  val id: String,
  val name: String,
  val phone: String = "",
  val category: PersonCategory? = null,
  val whereWeMet: String = "",
  val email: String = "",
  val organisation: String = "",
  val designation: String = "",
  val service: String = "",
  val cadre: String = "",
  val serviceBatch: String = "",
  val foundationCourseBatch: String = "",
  val trainingGroup: String = "",
  val tags: List<String> = emptyList(),
  val postings: List<PostingEntry> = emptyList(),
  val introductions: List<IntroductionRecord> = emptyList(),
  val photoFileName: String? = null,
  val isFavourite: Boolean = false,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
) {
  fun currentPosting(): PostingEntry? = postings.firstOrNull { it.isCurrent }

  fun withSyncedPostings(): PersonProfile {
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
        postings = ensuredPostings,
        organisation = current.organisation,
        designation = current.designation,
      )
    } else {
      copy(postings = ensuredPostings)
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
