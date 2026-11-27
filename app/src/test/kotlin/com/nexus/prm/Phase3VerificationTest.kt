package com.nexus.prm

import com.nexus.prm.data.AutoBackupRunner
import com.nexus.prm.data.BackupCrypto
import com.nexus.prm.data.BackupDestination
import com.nexus.prm.data.CsvExporter
import com.nexus.prm.data.IntroductionRecord
import com.nexus.prm.data.LocalDirectoryRepository
import com.nexus.prm.data.LocalStorageCrypto
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.PostingEntry
import com.nexus.prm.data.PostingValidation
import com.nexus.prm.data.RestoreMerger
import com.nexus.prm.data.ScheduledBackupFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Phase3VerificationTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private class TestXorCrypto : LocalStorageCrypto {
    override fun encrypt(plaintext: ByteArray): ByteArray =
      ByteArray(plaintext.size) { i -> (plaintext[i].toInt() xor 0x5A).toByte() }

    override fun decrypt(ciphertext: ByteArray): ByteArray =
      ByteArray(ciphertext.size) { i -> (ciphertext[i].toInt() xor 0x5A).toByte() }
  }

  private class FileBackupDestination(
    private val dir: File,
    var accessible: Boolean = true,
  ) : BackupDestination {
    override fun isAccessible(): Boolean = accessible && dir.exists()

    override fun listScheduledFiles(): List<ScheduledBackupFile> =
      dir.listFiles()
        ?.filter { it.isFile && AutoBackupRunner.isScheduledBackupFileName(it.name) }
        ?.map { ScheduledBackupFile(it.name) }
        .orEmpty()

    override fun writeAndReadBack(fileName: String, bytes: ByteArray): ByteArray {
      if (!accessible) error("Destination unavailable")
      val target = File(dir, fileName)
      target.writeBytes(bytes)
      return target.readBytes()
    }

    override fun deleteFile(fileName: String): Boolean =
      File(dir, fileName).delete()
  }

  private fun newRepo(dir: File = tempFolder.newFolder()): LocalDirectoryRepository =
    LocalDirectoryRepository(filesDir = dir, crypto = TestXorCrypto())

  @Test
  fun P3_01_upgradeAndPostings_preservesInitialRoleAndTracksHistory() = runTest {
    val dir = tempFolder.newFolder()
    val crypto = TestXorCrypto()
    // Phase 2.1 snapshot (schemaVersion = 2, no postings/introductions)
    val phase21Json = """
      {
        "schemaVersion": 2,
        "profiles": [
          {
            "id": "officer-1",
            "name": "Meera Test",
            "organisation": "District Collectorate",
            "designation": "Assistant Collector",
            "createdAtEpochMs": 1000,
            "updatedAtEpochMs": 1000
          }
        ]
      }
    """.trimIndent()
    File(dir, "nexus_directory.enc").writeBytes(crypto.encrypt(phase21Json.encodeToByteArray()))

    val repo = LocalDirectoryRepository(filesDir = dir, crypto = crypto)
    val initial = repo.getProfile("officer-1")!!
    assertEquals(1, initial.postings.size)
    assertEquals("District Collectorate", initial.postings.first().organisation)
    assertEquals("Assistant Collector", initial.postings.first().designation)
    assertTrue(initial.postings.first().isCurrent)

    // Reject end date earlier than start date (both YYYY-MM-DD and DD-MM-YYYY)
    assertFalse(PostingValidation.isValidDateRange("2026-05-01", "2026-04-01"))
    assertFalse(PostingValidation.isValidDateRange("01-05-2026", "01-04-2026"))
    assertTrue(PostingValidation.isValidDateRange("01-05-2026", "01-06-2026"))
    assertTrue(PostingValidation.isValidDate("29-02-2024"))
    assertFalse(PostingValidation.isValidDate("29-02-2025"))
    assertEquals("01-05-2026", PostingValidation.formatForDisplay("2026-05-01"))
    var rejected = false
    try {
      repo.addOrUpdatePosting(
        "officer-1",
        PostingEntry(
          id = "bad",
          organisation = "Test",
          designation = "Role",
          startDate = "2026-05-01",
          endDate = "2026-04-01",
        ),
      )
    } catch (_: IllegalArgumentException) {
      rejected = true
    }
    assertTrue(rejected)

    // Add new current role -> previous role retained in history
    repo.addOrUpdatePosting(
      "officer-1",
      PostingEntry(
        id = "post-2",
        organisation = "Ministry of Health",
        designation = "Deputy Secretary",
        location = "New Delhi",
        startDate = "2026-06-01",
        isCurrent = true,
      ),
    )

    val reloaded = LocalDirectoryRepository(filesDir = dir, crypto = crypto)
    val meera = reloaded.getProfile("officer-1")!!
    assertEquals(2, meera.postings.size)
    assertEquals("Ministry of Health", meera.organisation)
    assertEquals("Deputy Secretary", meera.designation)
    assertEquals("post-2", meera.currentPosting()?.id)
    assertFalse(meera.postings.first { it.id == "initial-officer-1" }.isCurrent)
  }

  @Test
  fun P3_02_introductions_linkedAndTyped_remainReadableAfterLinkedRemoval() = runTest {
    val repo = newRepo()
    val introducer = repo.saveProfile(
      PersonProfile(id = "intro-1", name = "Senior Mentor", createdAtEpochMs = 100L, updatedAtEpochMs = 100L),
    )
    val target = repo.saveProfile(
      PersonProfile(id = "target-1", name = "Meera Test", createdAtEpochMs = 200L, updatedAtEpochMs = 200L),
    )

    repo.addIntroduction(
      target.id,
      IntroductionRecord(
        id = "i1",
        introducerProfileId = introducer.id,
        introducerName = introducer.name,
        date = "2026-10-15",
        contextNote = "Faculty lounge",
      ),
    )
    repo.addIntroduction(
      target.id,
      IntroductionRecord(
        id = "i2",
        introducerProfileId = null,
        introducerName = "Typed Guest Name",
      ),
    )

    // Delete linked profile
    repo.deleteProfile(introducer.id)
    val updated = repo.getProfile(target.id)!!
    assertEquals(2, updated.introductions.size)
    assertEquals("Senior Mentor", updated.introductions[0].introducerName)
    assertEquals("Typed Guest Name", updated.introductions[1].introducerName)
  }

  @Test
  fun P3_03_backupCompatibility_cleanRestoreIdempotentMergeAndOlderBackup() = runTest {
    val sourceRepo = newRepo()
    val p = sourceRepo.saveProfile(
      PersonProfile(id = "p1", name = "Aditi Test", organisation = "Org A", designation = "Role A", createdAtEpochMs = 100L, updatedAtEpochMs = 100L),
    )
    sourceRepo.addOrUpdatePosting(
      p.id,
      PostingEntry(id = "post-new", organisation = "Org B", designation = "Role B", isCurrent = true),
    )
    sourceRepo.addIntroduction(
      p.id,
      IntroductionRecord(id = "intro-a", introducerName = "Mentor X", date = "2026-09-01"),
    )
    val p3Backup = sourceRepo.exportBackup("P3Pass!")

    // Clean second phone restore
    val cleanPhone = newRepo()
    val p3Preview = cleanPhone.inspectBackup(p3Backup, "P3Pass!")
    cleanPhone.applyBackup(p3Preview)
    val restored = cleanPhone.getProfile("p1")!!
    assertEquals(2, restored.postings.size)
    assertEquals(1, restored.introductions.size)
    assertEquals("Org B", restored.organisation)

    // Merge twice into populated directory -> zero duplicates
    val populated = newRepo()
    populated.saveProfile(
      PersonProfile(id = "p1", name = "Aditi Local", organisation = "Org Local", designation = "Role Local", createdAtEpochMs = 50L, updatedAtEpochMs = 50L),
    )
    val merge1 = populated.inspectBackup(p3Backup, "P3Pass!")
    populated.applyMergeRestore(merge1)
    val after1 = populated.getProfile("p1")!!
    assertEquals("Org Local", after1.organisation) // local current role preserved
    assertEquals(2, after1.postings.size)
    assertEquals(1, after1.introductions.size)

    val merge2 = populated.inspectBackup(p3Backup, "P3Pass!")
    populated.applyMergeRestore(merge2)
    val after2 = populated.getProfile("p1")!!
    assertEquals(2, after2.postings.size)
    assertEquals(1, after2.introductions.size)

    // Older Phase 2 backup merged into Phase 3 directory leaves Phase 3 postings/intros intact
    val phase2Json = """
      {
        "schemaVersion": 2,
        "createdAtEpochMs": 1000,
        "profiles": [
          {
            "id": "p2-only",
            "name": "Phase 2 Person",
            "createdAtEpochMs": 1000,
            "updatedAtEpochMs": 1000
          }
        ]
      }
    """.trimIndent()
    val zipBytes = ByteArrayOutputStream().use { baos ->
      ZipOutputStream(baos).use { zip ->
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(phase2Json.encodeToByteArray())
        zip.closeEntry()
      }
      baos.toByteArray()
    }
    val phase2Backup = BackupCrypto.encrypt(zipBytes, "P2Pass")
    populated.applyMergeRestore(populated.inspectBackup(phase2Backup, "P2Pass"))
    assertEquals(2, populated.profiles.value.size)
    assertEquals(2, populated.getProfile("p1")!!.postings.size)
    assertEquals(1, populated.getProfile("p1")!!.introductions.size)
  }

  @Test
  fun P3_04_scheduleAndRetention_keepsSevenLatestAndPreservesManualOnFailure() = runTest {
    val repo = newRepo()
    repo.saveProfile(PersonProfile(id = "1", name = "Aditi Test", createdAtEpochMs = 100L, updatedAtEpochMs = 100L))

    val destDir = tempFolder.newFolder("auto_dest")
    val manualFile = File(destDir, "manual-backup.nxbk").apply { writeText("manual") }
    val unrelatedFile = File(destDir, "notes.txt").apply { writeText("unrelated") }

    val destination = FileBackupDestination(destDir, accessible = true)
    val runner = AutoBackupRunner()

    var lastCreated = ""
    for (i in 1..8) {
      val result = runner.execute(
        repository = repo,
        destination = destination,
        password = "AutoPass123",
        retentionCount = 7,
        nowEpochMs = 1_700_000_000_000L + i * 1000L,
      )
      assertTrue(result.isSuccess)
      lastCreated = result.getOrThrow()
    }

    val scheduled = destination.listScheduledFiles()
    assertEquals(7, scheduled.size)
    assertTrue(scheduled.any { it.name == lastCreated })
    assertTrue(manualFile.exists())
    assertTrue(unrelatedFile.exists())

    // Simulate destination failure: existing 7 files remain intact
    destination.accessible = false
    val failed = runner.execute(
      repository = repo,
      destination = destination,
      password = "AutoPass123",
      retentionCount = 7,
      nowEpochMs = 1_700_000_099_000L,
    )
    assertTrue(failed.isFailure)
    destination.accessible = true
    assertEquals(7, destination.listScheduledFiles().size)
  }

  @Test
  fun P3_05_restoreSafeguards_autoBackupRestorableWithMergeDefault() = runTest {
    val sourceRepo = newRepo()
    sourceRepo.saveProfile(PersonProfile(id = "auto-1", name = "Auto Backup Person", createdAtEpochMs = 100L, updatedAtEpochMs = 100L))
    val destDir = tempFolder.newFolder("auto_restore")
    val destination = FileBackupDestination(destDir)
    val runner = AutoBackupRunner()
    val res = runner.execute(sourceRepo, destination, "AutoPass!", nowEpochMs = 1_700_000_000_000L)
    val file = File(destDir, res.getOrThrow())

    val targetRepo = newRepo()
    targetRepo.saveProfile(PersonProfile(id = "loc-1", name = "Local Person", createdAtEpochMs = 50L, updatedAtEpochMs = 50L))

    val preview = targetRepo.inspectBackup(file.readBytes(), "AutoPass!")
    val mergePreview = RestoreMerger.buildPreview(targetRepo.profiles.value, preview)
    assertEquals(1, mergePreview.toAdd.size)
    assertEquals(1, mergePreview.localOnlyCount)

    // Cancelling before apply leaves directory intact
    assertEquals(1, targetRepo.profiles.value.size)

    // Applying merge adds auto-backup profile while keeping local profile
    targetRepo.applyMergeRestore(preview)
    assertEquals(2, targetRepo.profiles.value.size)
  }

  @Test
  fun P3_06_csvExport_notesOptInAndQuotingAndFormulaProtection() {
    val profiles = listOf(
      PersonProfile(
        id = "1",
        name = "Riya, Test",
        phone = "+91 90000 01004",
        whereWeMet = "Line 1\n\"Mess, Hall A\"",
        organisation = "=SUM(A1:A2)",
        designation = "Officer",
        postings = listOf(
          PostingEntry(id = "p1", organisation = "Current Org", designation = "Director", isCurrent = true),
        ),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
    )

    val withoutNotes = CsvExporter.export(profiles, includeMeetingNotes = false)
    assertFalse(withoutNotes.contains("where_we_met"))
    assertFalse(withoutNotes.contains("Mess, Hall A"))
    assertTrue(withoutNotes.contains("\"Riya, Test\""))
    assertTrue(withoutNotes.contains("'+91 90000 01004"))
    assertTrue(withoutNotes.contains("Current Org"))
    assertTrue(withoutNotes.contains("Director"))

    val withNotes = CsvExporter.export(profiles, includeMeetingNotes = true)
    assertTrue(withNotes.contains("where_we_met"))
    assertTrue(withNotes.contains("\"Line 1\n\"\"Mess, Hall A\"\"\""))
  }
}
