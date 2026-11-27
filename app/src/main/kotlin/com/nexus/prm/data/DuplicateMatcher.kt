package com.nexus.prm.data

enum class DuplicateMatchField(val label: String) {
  PHONE("Phone"),
  EMAIL("Email"),
  PHONE_AND_EMAIL("Phone & Email"),
}

data class DuplicateMatch(
  val existingProfile: PersonProfile,
  val matchedField: DuplicateMatchField,
)

object DuplicateMatcher {
  fun normalizePhone(raw: String): String = raw.filter { it.isDigit() }

  fun normalizeEmail(raw: String): String = raw.trim().lowercase()

  fun findMatch(
    phone: String,
    email: String,
    candidates: List<PersonProfile>,
    excludeProfileId: String? = null,
  ): DuplicateMatch? = findMatch(
    phones = listOf(phone),
    emails = listOf(email),
    candidates = candidates,
    excludeProfileId = excludeProfileId,
  )

  fun findMatch(
    phones: List<String>,
    emails: List<String>,
    candidates: List<PersonProfile>,
    excludeProfileId: String? = null,
  ): DuplicateMatch? {
    val normPhones = phones.map(::normalizePhone).filter { it.isNotEmpty() }.toSet()
    val normEmails = emails.map(::normalizeEmail).filter { it.isNotEmpty() }.toSet()
    if (normPhones.isEmpty() && normEmails.isEmpty()) return null

    for (candidate in candidates) {
      if (excludeProfileId != null && candidate.id == excludeProfileId) continue
      val candidatePhones = candidate.allPhoneValues().map(::normalizePhone).filter { it.isNotEmpty() }.toSet()
      val candidateEmails = candidate.allEmailValues().map(::normalizeEmail).filter { it.isNotEmpty() }.toSet()

      val phoneMatches = normPhones.isNotEmpty() && candidatePhones.any { it in normPhones }
      val emailMatches = normEmails.isNotEmpty() && candidateEmails.any { it in normEmails }
      val field = when {
        phoneMatches && emailMatches -> DuplicateMatchField.PHONE_AND_EMAIL
        phoneMatches -> DuplicateMatchField.PHONE
        emailMatches -> DuplicateMatchField.EMAIL
        else -> null
      }
      if (field != null) {
        return DuplicateMatch(existingProfile = candidate, matchedField = field)
      }
    }
    return null
  }
}
