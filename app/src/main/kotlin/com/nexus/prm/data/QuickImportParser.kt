package com.nexus.prm.data

sealed interface QuickCaptureResult {
  data class SingleValue(
    val kind: Kind,
    val value: String,
  ) : QuickCaptureResult

  data class Invalid(val message: String) : QuickCaptureResult

  enum class Kind { PHONE, EMAIL }
}

data class ParsedVCardContact(
  val name: String,
  val phones: List<LabeledContactValue>,
  val emails: List<LabeledContactValue>,
  val organisation: String = "",
  val designation: String = "",
)

object QuickImportParser {
  private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
  private val PHONE_ALLOWED_REGEX = Regex("^[+0-9()\\s.-]{7,24}$")

  fun parseSelectedText(raw: String?): QuickCaptureResult {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) {
      return QuickCaptureResult.Invalid("No phone number or email address was selected.")
    }
    if (trimmed.contains('\n') || trimmed.contains('\r')) {
      return QuickCaptureResult.Invalid(
        "Select a single phone number or email address rather than a whole message.",
      )
    }

    if (EMAIL_REGEX.matches(trimmed)) {
      return QuickCaptureResult.SingleValue(QuickCaptureResult.Kind.EMAIL, trimmed)
    }

    val digitCount = trimmed.count { it.isDigit() }
    if (PHONE_ALLOWED_REGEX.matches(trimmed) && digitCount in 7..15) {
      return QuickCaptureResult.SingleValue(QuickCaptureResult.Kind.PHONE, trimmed)
    }

    return QuickCaptureResult.Invalid(
      "Could not recognise a single phone number or email address. Select just one number or email and try again.",
    )
  }

  fun parseSingleVCard(raw: String): Result<ParsedVCardContact> = runCatching {
    val unfolded = unfoldLines(raw)
    if (unfolded.isEmpty()) {
      throw IllegalArgumentException("The selected vCard file is empty.")
    }

    var beginCount = 0
    var endCount = 0
    var inCard = false
    val cardLines = mutableListOf<String>()

    for (line in unfolded) {
      val upper = line.trim().uppercase()
      when (upper) {
        "BEGIN:VCARD" -> {
          if (inCard) throw IllegalArgumentException("The selected vCard file is malformed.")
          beginCount += 1
          if (beginCount > 1) {
            throw IllegalArgumentException(
              "This vCard file contains multiple contacts. Only one contact per file is supported.",
            )
          }
          inCard = true
        }
        "END:VCARD" -> {
          if (!inCard) throw IllegalArgumentException("The selected vCard file is malformed.")
          endCount += 1
          inCard = false
        }
        else -> {
          if (inCard && line.isNotBlank()) {
            cardLines += line
          }
        }
      }
    }

    if (beginCount == 0 || endCount != 1 || inCard) {
      throw IllegalArgumentException("The selected file is not a valid single-contact vCard.")
    }

    var fnName = ""
    var structuredName = ""
    var organisation = ""
    var designation = ""
    val phones = mutableListOf<LabeledContactValue>()
    val emails = mutableListOf<LabeledContactValue>()

    for (line in cardLines) {
      val colonIdx = line.indexOf(':')
      if (colonIdx <= 0) continue
      val header = line.substring(0, colonIdx)
      val rawValue = line.substring(colonIdx + 1)

      val headerParts = header.split(';')
      val propName = headerParts.first().substringAfterLast('.').trim().uppercase()
      val params = headerParts.drop(1)
      val decoded = decodeValue(rawValue, params)

      when (propName) {
        "FN" -> if (fnName.isBlank()) fnName = unescapeVCard(decoded).trim()
        "N" -> {
          if (structuredName.isBlank()) {
            val parts = decoded.split(';').map { unescapeVCard(it).trim() }
            // N format: Family;Given;Additional;Prefix;Suffix
            val assembled = listOfNotNull(
              parts.getOrNull(3)?.takeIf { it.isNotEmpty() },
              parts.getOrNull(1)?.takeIf { it.isNotEmpty() },
              parts.getOrNull(2)?.takeIf { it.isNotEmpty() },
              parts.getOrNull(0)?.takeIf { it.isNotEmpty() },
              parts.getOrNull(4)?.takeIf { it.isNotEmpty() },
            ).joinToString(" ")
            structuredName = assembled.trim()
          }
        }
        "TEL" -> {
          val value = unescapeVCard(decoded).trim()
          if (value.isNotEmpty()) {
            val (label, isPref) = parseContactParams(params)
            phones += LabeledContactValue(value = value, label = label, isPrimary = isPref)
          }
        }
        "EMAIL" -> {
          val value = unescapeVCard(decoded).trim()
          if (value.isNotEmpty()) {
            val (label, isPref) = parseContactParams(params)
            emails += LabeledContactValue(value = value, label = label, isPrimary = isPref)
          }
        }
        "ORG" -> {
          if (organisation.isBlank()) {
            organisation = unescapeVCard(decoded.substringBefore(';')).trim()
          }
        }
        "TITLE" -> {
          if (designation.isBlank()) {
            designation = unescapeVCard(decoded).trim()
          }
        }
      }
    }

    val resolvedName = fnName.ifBlank { structuredName }
    require(resolvedName.isNotEmpty()) {
      "The vCard file is missing a contact name."
    }

    ParsedVCardContact(
      name = resolvedName,
      phones = PersonProfile.normalizeContactList(phones, normalizer = DuplicateMatcher::normalizePhone),
      emails = PersonProfile.normalizeContactList(emails, normalizer = DuplicateMatcher::normalizeEmail),
      organisation = organisation,
      designation = designation,
    )
  }

  private fun unfoldLines(raw: String): List<String> {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
    val result = mutableListOf<String>()
    for (line in normalized.split('\n')) {
      if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
        result[result.lastIndex] = result.last() + line.drop(1)
      } else {
        result += line
      }
    }
    return result
  }

  private fun parseContactParams(params: List<String>): Pair<String, Boolean> {
    var customLabel: String? = null
    var isHome = false
    var isWork = false
    var isPref = false
    var otherTypeLabel: String? = null

    val ignoredTypes = setOf("CELL", "VOICE", "INTERNET", "PREF", "MSG", "TEXT", "MAIN")
    for (param in params) {
      val trimmed = param.trim()
      val eqIdx = trimmed.indexOf('=')
      if (eqIdx > 0) {
        val key = trimmed.substring(0, eqIdx).trim().uppercase()
        val valPart = trimmed.substring(eqIdx + 1).trim()
        when (key) {
          "X-NEXUS-LABEL" -> if (valPart.isNotEmpty()) customLabel = valPart
          "PREF" -> isPref = true
          "TYPE" -> {
            for (token in valPart.split(',')) {
              val t = token.trim().uppercase()
              when (t) {
                "HOME" -> isHome = true
                "WORK" -> isWork = true
                "PREF" -> isPref = true
                else -> if (t.isNotEmpty() && t !in ignoredTypes && otherTypeLabel == null) {
                  otherTypeLabel = token.trim()
                }
              }
            }
          }
        }
      } else {
        val upper = trimmed.uppercase()
        when (upper) {
          "HOME" -> isHome = true
          "WORK" -> isWork = true
          "PREF" -> isPref = true
        }
      }
    }

    val label = when {
      !customLabel.isNullOrBlank() -> customLabel
      isWork -> LabeledContactValue.LABEL_OFFICE
      isHome -> LabeledContactValue.LABEL_PERSONAL
      !otherTypeLabel.isNullOrBlank() -> otherTypeLabel
      else -> LabeledContactValue.DEFAULT_LABEL
    }
    return label to isPref
  }

  private fun decodeValue(raw: String, params: List<String>): String {
    val isQp = params.any { it.trim().equals("ENCODING=QUOTED-PRINTABLE", ignoreCase = true) }
    if (!isQp) return raw
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < raw.length) {
      if (raw[i] == '=' && i + 2 < raw.length) {
        val hex = raw.substring(i + 1, i + 3)
        val parsed = hex.toIntOrNull(16)
        if (parsed != null) {
          bytes += parsed.toByte()
          i += 3
          continue
        }
      }
      bytes += raw[i].code.toByte()
      i += 1
    }
    return bytes.toByteArray().toString(Charsets.UTF_8)
  }

  private fun unescapeVCard(value: String): String =
    value
      .replace("\\n", "\n")
      .replace("\\N", "\n")
      .replace("\\,", ",")
      .replace("\\;", ";")
      .replace("\\\\", "\\")
}
