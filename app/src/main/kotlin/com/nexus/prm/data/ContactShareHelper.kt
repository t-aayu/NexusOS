package com.nexus.prm.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ContactShareHelper {
  fun shareContact(context: Context, profile: PersonProfile) {
    val payload = OwnerCardVCard.buildContactVCard(profile)
    val safeBase = sanitizeBaseName(profile.name, fallback = "contact")
    val uri = writeSharedFile(context, "$safeBase.vcf") { file ->
      file.writeText(payload, Charsets.UTF_8)
    }
    launchFileShare(
      context = context,
      uri = uri,
      mimeType = "text/x-vcard",
      subject = profile.name.trim(),
      chooserTitle = "Share contact",
    )
  }

  fun shareOwnerText(context: Context, card: OwnerCard) {
    val preview = OwnerCardVCard.buildPreview(card)
    val text = OwnerCardVCard.buildOwnerShareText(preview)
    if (text.isBlank()) return
    val subject = card.shareableName().ifEmpty { "My contact card" }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
      putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share my info"))
  }

  fun shareOwnerVCard(context: Context, card: OwnerCard) {
    val preview = OwnerCardVCard.buildPreview(card)
    if (preview.vCardPayload.isBlank()) return
    val safeBase = sanitizeBaseName(card.shareableName(), fallback = "my_contact_card")
    val uri = writeSharedFile(context, "$safeBase.vcf") { file ->
      file.writeText(preview.vCardPayload, Charsets.UTF_8)
    }
    launchFileShare(
      context = context,
      uri = uri,
      mimeType = "text/x-vcard",
      subject = card.shareableName().ifEmpty { "My contact card" },
      chooserTitle = "Share my vCard",
    )
  }

  fun shareOwnerQrImage(context: Context, card: OwnerCard) {
    val preview = OwnerCardVCard.buildPreview(card)
    val bitmap = OwnerCardVCard.generateQrBitmap(
      payload = preview.vCardPayload,
      sizePx = 768,
      margin = 3,
    ) ?: return
    val safeBase = sanitizeBaseName(card.shareableName(), fallback = "my_contact_card")
    val uri = writeSharedFile(context, "$safeBase-qr.png") { file ->
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      }
    }
    launchFileShare(
      context = context,
      uri = uri,
      mimeType = "image/png",
      subject = card.shareableName().ifEmpty { "My contact card QR" },
      chooserTitle = "Share QR code",
    )
  }

  private fun sanitizeBaseName(raw: String, fallback: String): String =
    raw.trim()
      .replace(Regex("[^A-Za-z0-9._-]+"), "_")
      .trim('_')
      .ifEmpty { fallback }

  private fun writeSharedFile(
    context: Context,
    fileName: String,
    writer: (File) -> Unit,
  ): Uri {
    val shareDir = File(context.cacheDir, "shared_contacts")
    if (!shareDir.exists()) shareDir.mkdirs()
    val targetFile = File(shareDir, fileName)
    writer(targetFile)
    return FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      targetFile,
    )
  }

  private fun launchFileShare(
    context: Context,
    uri: Uri,
    mimeType: String,
    subject: String,
    chooserTitle: String,
  ) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      if (subject.isNotBlank()) {
        putExtra(Intent.EXTRA_SUBJECT, subject)
      }
      clipData = ClipData.newRawUri(subject.ifBlank { chooserTitle }, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, chooserTitle)
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
  }
}
