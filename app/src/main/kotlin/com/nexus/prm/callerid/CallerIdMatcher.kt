package com.nexus.prm.callerid

import com.nexus.prm.data.DuplicateMatcher
import com.nexus.prm.data.PersonProfile

object CallerIdMatcher {

  fun canonicalPhoneVariants(raw: String): Set<String> {
    val digits = DuplicateMatcher.normalizePhone(raw)
    if (digits.isEmpty()) return emptySet()

    val variants = linkedSetOf(digits)
    when {
      digits.length == 12 && digits.startsWith("91") -> {
        variants += digits.substring(2)
      }
      digits.length == 11 && digits.startsWith("0") -> {
        val tenDigit = digits.substring(1)
        variants += tenDigit
        variants += "91$tenDigit"
      }
      digits.length == 10 -> {
        variants += "91$digits"
      }
    }
    return variants
  }

  fun findUnambiguousMatch(
    incomingNumber: String?,
    profiles: List<PersonProfile>,
  ): PersonProfile? {
    val incoming = incomingNumber?.trim().orEmpty()
    if (incoming.isEmpty()) return null
    val incomingVariants = canonicalPhoneVariants(incoming)
    if (incomingVariants.isEmpty()) return null

    val matches = profiles.filter { profile ->
      profile.allPhoneValues().any { phoneValue ->
        canonicalPhoneVariants(phoneValue).any { it in incomingVariants }
      }
    }
    return if (matches.size == 1) matches.single() else null
  }

  fun formatCallerSubtitle(profile: PersonProfile): String {
    val service = profile.service.trim()
    val serviceBatch = profile.serviceBatch.trim().takeIf { it.isNotEmpty() }?.let { raw ->
      if (raw.contains("batch", ignoreCase = true)) raw else "$raw batch"
    }.orEmpty()
    val cadre = profile.cadre.trim().takeIf { it.isNotEmpty() }?.let { raw ->
      if (raw.contains("cadre", ignoreCase = true)) raw else "$raw cadre"
    }.orEmpty()

    val current = profile.currentPosting()
    val designation = (current?.designation?.trim()?.takeIf { it.isNotEmpty() }
      ?: profile.designation.trim())
    val organisation = (current?.organisation?.trim()?.takeIf { it.isNotEmpty() }
      ?: profile.organisation.trim())

    val primary = listOf(service, serviceBatch, cadre).filter { it.isNotEmpty() }
    val fallback = listOf(designation, organisation).filter { it.isNotEmpty() }

    return (primary + fallback).take(3).joinToString(" \u00B7 ")
  }
}
