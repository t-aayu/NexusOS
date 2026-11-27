package com.nexus.prm.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

data class OwnerCardSharePreview(
  val lines: List<Pair<String, String>>,
  val vCardPayload: String,
)

object OwnerCardVCard {
  fun buildPreview(card: OwnerCard): OwnerCardSharePreview {
    val lines = mutableListOf<Pair<String, String>>()
    val vcard = StringBuilder().apply {
      appendLine("BEGIN:VCARD")
      appendLine("VERSION:3.0")
    }

    val name = card.name.trim()
    if (card.shareName && name.isNotEmpty()) {
      lines += "Name" to name
      vcard.appendLine("FN:${escapeVCard(name)}")
      vcard.appendLine("N:;${escapeVCard(name)};;;")
    }

    val phone = card.phone.trim()
    if (card.sharePhone && phone.isNotEmpty()) {
      lines += "Phone" to phone
      vcard.appendLine("TEL;TYPE=CELL:${escapeVCard(phone)}")
    }

    val email = card.email.trim()
    if (card.shareEmail && email.isNotEmpty()) {
      lines += "Email" to email
      vcard.appendLine("EMAIL:${escapeVCard(email)}")
    }

    val org = card.organisation.trim()
    if (card.shareOrganisation && org.isNotEmpty()) {
      lines += "Organisation" to org
      vcard.appendLine("ORG:${escapeVCard(org)}")
    }

    val title = card.designation.trim()
    if (card.shareDesignation && title.isNotEmpty()) {
      lines += "Designation" to title
      vcard.appendLine("TITLE:${escapeVCard(title)}")
    }

    val noteParts = mutableListOf<String>()
    val service = card.service.trim()
    if (card.shareService && service.isNotEmpty()) {
      lines += "Service" to service
      noteParts += "Service: $service"
    }
    val cadre = card.cadre.trim()
    if (card.shareCadre && cadre.isNotEmpty()) {
      lines += "Cadre" to cadre
      noteParts += "Cadre: $cadre"
    }
    val serviceBatch = card.serviceBatch.trim()
    if (card.shareServiceBatch && serviceBatch.isNotEmpty()) {
      lines += "Service batch" to serviceBatch
      noteParts += "Service batch: $serviceBatch"
    }
    val fcBatch = card.foundationCourseBatch.trim()
    if (card.shareFoundationCourseBatch && fcBatch.isNotEmpty()) {
      lines += "Foundation Course batch" to fcBatch
      noteParts += "FC batch: $fcBatch"
    }
    val trainingGroup = card.trainingGroup.trim()
    if (card.shareTrainingGroup && trainingGroup.isNotEmpty()) {
      lines += "Training group" to trainingGroup
      noteParts += "Training group: $trainingGroup"
    }

    if (noteParts.isNotEmpty()) {
      vcard.appendLine("NOTE:${escapeVCard(noteParts.joinToString(" | "))}")
    }

    vcard.append("END:VCARD")
    return OwnerCardSharePreview(
      lines = lines,
      vCardPayload = if (lines.isEmpty()) "" else vcard.toString(),
    )
  }

  /**
   * Builds a minimal vCard 3.0 payload for sharing a contact, intentionally omitting
   * private fields (whereWeMet, otherDetails, tags, groups, photos, postings, introductions).
   */
  fun buildContactVCard(profile: PersonProfile): String {
    val name = profile.name.trim()
    val phone = profile.phone.trim()
    val email = profile.email.trim()
    return StringBuilder().apply {
      appendLine("BEGIN:VCARD")
      appendLine("VERSION:3.0")
      if (name.isNotEmpty()) {
        appendLine("FN:${escapeVCard(name)}")
        appendLine("N:;${escapeVCard(name)};;;")
      }
      if (phone.isNotEmpty()) {
        appendLine("TEL;TYPE=CELL:${escapeVCard(phone)}")
      }
      if (email.isNotEmpty()) {
        appendLine("EMAIL:${escapeVCard(email)}")
      }
      append("END:VCARD")
    }.toString()
  }

  fun generateQrBitmap(payload: String, sizePx: Int = 640): Bitmap? {
    if (payload.isBlank()) return null
    val hints = mapOf(
      EncodeHintType.CHARACTER_SET to "UTF-8",
      EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
      for (y in 0 until sizePx) {
        bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
      }
    }
    return bitmap
  }

  private fun escapeVCard(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
      .replace(";", "\\;")
      .replace(",", "\\,")
}
