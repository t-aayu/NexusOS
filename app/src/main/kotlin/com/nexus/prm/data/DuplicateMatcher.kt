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
  ): DuplicateMatch? {
    val normPhone = normalizePhone(phone)
    val normEmail = normalizeEmail(email)
    if (normPhone.isEmpty() && normEmail.isEmpty()) return null

    for (candidate in candidates) {
      if (excludeProfileId != null && candidate.id == excludeProfileId) continue
      val phoneMatches = normPhone.isNotEmpty() && normalizePhone(candidate.phone) == normPhone
      val emailMatches = normEmail.isNotEmpty() && normalizeEmail(candidate.email) == normEmail
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
