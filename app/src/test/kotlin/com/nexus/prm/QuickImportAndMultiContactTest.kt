package com.nexus.prm

import com.nexus.prm.callerid.CallerIdMatcher
import com.nexus.prm.data.BackupCrypto
import com.nexus.prm.data.CsvExporter
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.DuplicateMatcher
import com.nexus.prm.data.LabeledContactValue
import com.nexus.prm.data.LocalDirectoryRepository
import com.nexus.prm.data.LocalStorageCrypto
import com.nexus.prm.data.OwnerCardVCard
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.QuickCaptureResult
import com.nexus.prm.data.QuickImportParser
import com.nexus.prm.ui.profile.ContactRowDraft
import com.nexus.prm.ui.profile.PersonEditDraft
import com.nexus.prm.ui.profile.formatLastUpdatedLabel
import com.nexus.prm.ui.profile.formatWhatsAppDigits
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuickImportAndMultiContactTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val passthroughCrypto = object : LocalStorageCrypto {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext
  }

  private fun newRepo(dir: File = tempFolder.newFolder("mc_${System.nanoTime()}")): LocalDirectoryRepository =
    LocalDirectoryRepository(filesDir = dir, crypto = passthroughCrypto)

  @Test
  fun multiContact_labelsPrimaryFallbackDedupTrailingRowSearchCallerIdAndCsv() = runTest {
    val repo = newRepo()
    val saved = repo.saveProfile(
      PersonProfile(
        id = "mc-1",
        name = "Priya Multi",
        phones = listOf(
          LabeledContactValue("+91 90000 01001", "Personal", isPrimary = false),
          LabeledContactValue("+91 90000 01002", "Office", isPrimary = true),
          LabeledContactValue("+91-90000-01001", "Duplicate", isPrimary = false), // same normalized phone -> skipped
          LabeledContactValue("9876543210", "Hostel", isPrimary = false),
          LabeledContactValue("   ", "Blank", isPrimary = false), // ignored
        ),
        emails = listOf(
          LabeledContactValue("priya.personal@example.com", "Personal", isPrimary = true),
          LabeledContactValue("priya.office@gov.in", "Office", isPrimary = false),
          LabeledContactValue("PRIYA.PERSONAL@example.com", "Dup", isPrimary = false),
        ),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
    )

    assertEquals(3, saved.phones.size)
    assertEquals("+91 90000 01002", saved.primaryPhone())
    assertEquals(2, saved.emails.size)
    assertEquals("priya.personal@example.com", saved.primaryEmail())

    // Trailing empty row draft behavior
    val rows = PersonEditDraft.ensureTrailingEmptyRow(
      listOf(
        ContactRowDraft(rowId = "r1", value = "111", label = "Personal", isPrimary = true),
        ContactRowDraft(rowId = "r2", value = "222", label = "Office", isPrimary = false),
      ),
    )
    assertEquals(3, rows.size)
    assertTrue(rows.last().value.isBlank())

    // Removing primary falls back to first remaining entry
    val afterRemovePrimary = PersonProfile.normalizeContactList(
      raw = saved.phones.filterNot { it.isPrimary },
      normalizer = DuplicateMatcher::normalizePhone,
    )
    assertEquals(2, afterRemovePrimary.size)
    assertTrue(afterRemovePrimary.first().isPrimary)
    assertEquals("+91 90000 01001", afterRemovePrimary.first().value)

    // Search & duplicate check find secondary values
    assertTrue(DirectorySearch.matchesQuery(saved, "9876543210"))
    assertTrue(DirectorySearch.matchesQuery(saved, "priya.office@gov.in"))
    assertNotNull(DuplicateMatcher.findMatch(phone = "9876543210", email = "", candidates = listOf(saved)))
    assertNotNull(DuplicateMatcher.findMatch(phone = "", email = "PRIYA.OFFICE@GOV.IN", candidates = listOf(saved)))

    // Caller ID matches secondary number, skips ambiguous cross-profile match
    assertEquals("mc-1", CallerIdMatcher.findUnambiguousMatch("+91 98765 43210", listOf(saved))?.id)
    val dupProfile = saved.copy(id = "mc-2", name = "Other", phones = listOf(LabeledContactValue("9876543210", "Personal", true)))
    assertNull(CallerIdMatcher.findUnambiguousMatch("+91 98765 43210", listOf(saved, dupProfile)))

    // CSV exports primary values
    val csv = CsvExporter.export(listOf(saved))
    assertTrue(csv.contains("'+91 90000 01002"))
    assertTrue(csv.contains("priya.personal@example.com"))
  }

  @Test
  fun multiContact_legacyUpgradeAndMergePreservesPrimaryAndAppendsUnique() = runTest {
    val legacyJson = """
      {
        "schemaVersion": 3,
        "createdAtEpochMs": 1000,
        "profiles": [
          {
            "id": "u1",
            "name": "Legacy Contact",
            "phone": "+91 90000 01001",
            "email": "legacy@example.com",
            "createdAtEpochMs": 1000,
            "updatedAtEpochMs": 1000
          }
        ]
      }
    """.trimIndent()
    val zipBytes = ByteArrayOutputStream().use { baos ->
      ZipOutputStream(baos).use { zip ->
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(legacyJson.encodeToByteArray())
        zip.closeEntry()
      }
      baos.toByteArray()
    }
    val legacyBackup = BackupCrypto.encrypt(zipBytes, "Pass1!")

    val localRepo = newRepo()
    localRepo.applyBackup(localRepo.inspectBackup(legacyBackup, "Pass1!"))
    val upgraded = localRepo.getProfile("u1")!!
    assertEquals(1, upgraded.resolvedPhones().size)
    assertTrue(upgraded.resolvedPhones().single().isPrimary)

    val sourceRepo = newRepo()
    sourceRepo.saveProfile(
      PersonProfile(
        id = "u1",
        name = "Legacy Contact",
        phones = listOf(
          LabeledContactValue("+91 90000 01001", "Personal", isPrimary = false),
          LabeledContactValue("+91 99999 88888", "Office", isPrimary = true),
        ),
        emails = listOf(
          LabeledContactValue("office@example.com", "Office", isPrimary = true),
        ),
        createdAtEpochMs = 2000L,
        updatedAtEpochMs = 2000L,
      ),
    )
    val multiBackup = sourceRepo.exportBackup("Pass2!")

    localRepo.applyMergeRestore(localRepo.inspectBackup(multiBackup, "Pass2!"))
    val merged = localRepo.getProfile("u1")!!
    assertEquals("+91 90000 01001", merged.primaryPhone()) // local primary preserved
    assertEquals(2, merged.phones.size)
    assertEquals(2, merged.emails.size)

    // Repeat merge adds zero duplicates
    localRepo.applyMergeRestore(localRepo.inspectBackup(multiBackup, "Pass2!"))
    assertEquals(2, localRepo.getProfile("u1")!!.phones.size)
    assertEquals(2, localRepo.getProfile("u1")!!.emails.size)
  }

  @Test
  fun quickImport_selectedTextAndSingleVCardRoundTripAndErrorRejection() {
    val phoneRes = QuickImportParser.parseSelectedText(" +91 98765 43210 ")
    assertTrue(phoneRes is QuickCaptureResult.SingleValue)
    assertEquals(QuickCaptureResult.Kind.PHONE, (phoneRes as QuickCaptureResult.SingleValue).kind)

    val emailRes = QuickImportParser.parseSelectedText("officer@gov.in")
    assertTrue(emailRes is QuickCaptureResult.SingleValue)
    assertEquals(QuickCaptureResult.Kind.EMAIL, (emailRes as QuickCaptureResult.SingleValue).kind)

    assertTrue(QuickImportParser.parseSelectedText("Call me at 9876543210 or email a@b.com").let { it is QuickCaptureResult.Invalid })
    assertTrue(QuickImportParser.parseSelectedText("Line 1\n9876543210").let { it is QuickCaptureResult.Invalid })
    assertTrue(QuickImportParser.parseSelectedText("   ").let { it is QuickCaptureResult.Invalid })

    val profile = PersonProfile(
      id = "vc-1",
      name = "Aarav Rao",
      phones = listOf(
        LabeledContactValue("+91 91111 22222", "Personal", isPrimary = false),
        LabeledContactValue("+91 93333 44444", "Office", isPrimary = true),
        LabeledContactValue("011-23456789", "Camp Office", isPrimary = false),
      ),
      emails = listOf(
        LabeledContactValue("aarav@example.com", "Personal", isPrimary = true),
        LabeledContactValue("aarav.rao@gov.in", "Office", isPrimary = false),
      ),
      whereWeMet = "Secret mess",
      otherDetails = "Private note",
      tags = listOf("HiddenTag"),
      createdAtEpochMs = 1L,
      updatedAtEpochMs = 1L,
    )
    val exportedVCard = OwnerCardVCard.buildContactVCard(profile)
    assertFalse(exportedVCard.contains("Secret mess"))
    assertFalse(exportedVCard.contains("Private note"))
    assertFalse(exportedVCard.contains("HiddenTag"))

    val parsed = QuickImportParser.parseSingleVCard(exportedVCard).getOrThrow()
    assertEquals("Aarav Rao", parsed.name)
    assertEquals(3, parsed.phones.size)
    assertEquals("+91 93333 44444", parsed.phones.first { it.isPrimary }.value)
    assertEquals("Office", parsed.phones.first { it.isPrimary }.label)
    assertEquals("Camp Office", parsed.phones[2].label)
    assertEquals(2, parsed.emails.size)
    assertEquals("aarav@example.com", parsed.emails.first { it.isPrimary }.value)

    // Reject multi-contact and malformed vCards
    val twoContact = "$exportedVCard\n$exportedVCard"
    assertTrue(QuickImportParser.parseSingleVCard(twoContact).isFailure)
    assertTrue(QuickImportParser.parseSingleVCard("NOT A VCARD").isFailure)
    assertTrue(QuickImportParser.parseSingleVCard("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD").isFailure)
  }

  @Test
  fun detailActions_deleteIntroductionWhatsAppDigitsAndLastUpdatedLabel() = runTest {
    val repo = newRepo()
    val introducer = repo.saveProfile(PersonProfile(id = "int-1", name = "Ravi", createdAtEpochMs = 100L, updatedAtEpochMs = 100L))
    val target = repo.saveProfile(PersonProfile(id = "tgt-1", name = "Meera", createdAtEpochMs = 100L, updatedAtEpochMs = 100L))
    repo.addIntroduction(target.id, com.nexus.prm.data.IntroductionRecord(id = "i1", introducerProfileId = introducer.id, introducerName = "Ravi"))
    repo.addIntroduction(target.id, com.nexus.prm.data.IntroductionRecord(id = "i2", introducerProfileId = null, introducerName = "Guest"))

    repo.deleteIntroduction(target.id, "i1")
    val afterDelete = repo.getProfile(target.id)!!
    assertEquals(1, afterDelete.introductions.size)
    assertEquals("i2", afterDelete.introductions.single().id)
    assertNotNull(repo.getProfile(introducer.id))

    assertEquals("919876543210", formatWhatsAppDigits("+91 98765 43210"))
    assertEquals("442079460958", formatWhatsAppDigits("0044 20 7946 0958"))
    assertNull(formatWhatsAppDigits(""))
    assertNull(formatWhatsAppDigits("12345"))
    assertNull(formatWhatsAppDigits("9876543210"))
    assertNull(formatWhatsAppDigits("09876543210"))

    val legacy = PersonProfile(id = "leg", name = "Legacy", createdAtEpochMs = 0L, updatedAtEpochMs = 0L)
    assertEquals("Last updated \u00B7 27 Nov 2026", formatLastUpdatedLabel(legacy))

    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
      set(2026, java.util.Calendar.NOVEMBER, 26, 12, 0, 0)
    }
    val dated = legacy.copy(updatedAtEpochMs = cal.timeInMillis)
    assertEquals("Last updated \u00B7 26 Nov 2026", formatLastUpdatedLabel(dated))
  }
}
