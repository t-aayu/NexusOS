package com.nexus.prm.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ContactShareHelper {
  fun shareContact(context: Context, profile: PersonProfile) {
    val payload = OwnerCardVCard.buildContactVCard(profile)
    val shareDir = File(context.cacheDir, "shared_contacts")
    if (!shareDir.exists()) shareDir.mkdirs()

    val safeBase = profile.name.trim()
      .replace(Regex("[^A-Za-z0-9._-]+"), "_")
      .trim('_')
      .ifEmpty { "contact" }
    val vcfFile = File(shareDir, "$safeBase.vcf")
    vcfFile.writeText(payload, Charsets.UTF_8)

    val uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      vcfFile,
    )

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/x-vcard"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, profile.name.trim())
      clipData = ClipData.newRawUri(profile.name.trim(), uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(sendIntent, "Share contact")
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
  }
}
