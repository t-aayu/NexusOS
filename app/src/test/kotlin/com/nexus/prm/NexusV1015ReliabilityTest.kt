package com.nexus.prm

import com.nexus.prm.data.BackupCrypto
import com.nexus.prm.data.BackupLimits
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.IntroductionRecord
import com.nexus.prm.data.LabeledContactValue
import com.nexus.prm.data.LocalDirectoryRepository
import com.nexus.prm.data.LocalStorageCrypto
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.PostingEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NexusV1015ReliabilityTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val passthroughCrypto = object : LocalStorageCrypto {
    override fun encrypt(plainBytes: ByteArray): ByteArray = plainBytes
    override fun decrypt(cipherBytes: ByteArray): ByteArray = cipherBytes
  }

  @Test
  fun nxq01_failedSave_preservesExistingDirectoryAndCleansOrphanPhoto() = runBlocking {
    val dir = tempFolder.newFolder("repo_nxq01")
    val repo = LocalDirectoryRepository(filesDir = dir, crypto = passthroughCrypto)
    val initial = repo.saveProfile(
      PersonProfile(id = "p-init", name = "Existing Contact", phone = "9876543210"),
      pendingPhotoBytes = byteArrayOf(1, 2, 3),
    )

    repo.beforeSnapshotPersistHook = { throw IllegalStateException("Simulated disk full") }
    try {
      repo.saveProfile(
        PersonProfile(id = "p-retry", name = "New Contact", phone = "9123456789"),
        pendingPhotoBytes = byteArrayOf(9, 8, 7),
      )
      fail("Expected saveProfile to throw on simulated disk failure")
    } catch (_: IllegalStateException) {
      // Expected
    }

    // Existing directory and in-memory state remain intact; orphan photo cleaned up
    assertEquals(listOf(initial.id), repo.profiles.value.map { it.id })
    val photosDir = dir.resolve("photos")
    assertEquals(1, photosDir.listFiles()?.size ?: 0)

    // Retry with same ID succeeds cleanly
    repo.beforeSnapshotPersistHook = null
    val saved = repo.saveProfile(
      PersonProfile(id = "p-retry", name = "New Contact", phone = "9123456789"),
      pendingPhotoBytes = byteArrayOf(9, 8, 7),
    )
    assertEquals("p-retry", saved.id)
    assertEquals(2, repo.profiles.value.size)
  }

  @Test
  fun nxq04_explainMatch_derivesConciseHintsWithoutLeakingNotesOrFullNumbers() {
    val profile = PersonProfile(
      id = "p1",
      name = "Aarav Mehta",
      phones = listOf(
        LabeledContactValue("+91 98111 22233", "Personal", isPrimary = true),
        LabeledContactValue("+91 99887 76655", "Office", isPrimary = false),
      ),
      whereWeMet = "Secret coffee shop behind court",
      otherDetails = "Private medical note",
      postings = listOf(
        PostingEntry(id = "1", organisation = "District HQ", designation = "DM", location = "Katihar", isCurrent = false),
        PostingEntry(id = "2", organisation = "Secretariat", designation = "Director", location = "Patna", isCurrent = true),
      ),
      introductions = listOf(
        IntroductionRecord(id = "i1", introducerName = "Ravi Shankar"),
      ),
    )

    assertNull(DirectorySearch.explainMatch(profile, ""))
    assertNull(DirectorySearch.explainMatch(profile, "aarav"))
    assertEquals("Past posting · Katihar", DirectorySearch.explainMatch(profile, "katihar"))
    assertEquals("Introduced by Ravi Shankar", DirectorySearch.explainMatch(profile, "ravi"))
    assertEquals("Match · Office number", DirectorySearch.explainMatch(profile, "76655"))
    val noteHint = DirectorySearch.explainMatch(profile, "medical")
    assertEquals("Match · Other details", noteHint)
    assertTrue(noteHint?.contains("Private") == false)
  }

  @Test
  fun nxq05_boundedBackupReads_rejectsOversizedStreamsEntriesAndCounts() = runBlocking {
    // 1. Bounded stream read rejects streams larger than limit
    val oversizedStream = ByteArrayInputStream(ByteArray(1024))
    try {
      BackupLimits.readBounded(oversizedStream, maxBytes = 512, label = "Test stream")
      fail("Expected readBounded to reject stream over 512 bytes")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message.orEmpty().contains("exceeds"))
    }

    // 2. Entry count > MAX_ENTRY_COUNT rejected in inspectBackup
    val dir = tempFolder.newFolder("repo_nxq05")
    val repo = LocalDirectoryRepository(filesDir = dir, crypto = passthroughCrypto)
    val existing = repo.saveProfile(PersonProfile(id = "p1", name = "Kept Contact"))

    val manyEntriesZip = ByteArrayOutputStream().use { baos ->
      ZipOutputStream(baos).use { zip ->
        for (i in 0..BackupLimits.MAX_ENTRY_COUNT) {
          zip.putNextEntry(ZipEntry("photos/f$i.jpg"))
          zip.closeEntry()
        }
      }
      baos.toByteArray()
    }
    val encryptedMany = BackupCrypto.encrypt(manyEntriesZip, "pass123")
    try {
      repo.inspectBackup(encryptedMany, "pass123")
      fail("Expected inspectBackup to reject > MAX_ENTRY_COUNT entries")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message.orEmpty().contains("too many entries"))
    }

    // Existing profile remains intact
    assertEquals(listOf(existing.id), repo.profiles.value.map { it.id })
  }

  @Test
  fun legacyTrainingFields_migrateIntoOtherDetails() {
    val legacy = PersonProfile(
      id = "p-legacy",
      name = "Legacy Officer",
      foundationCourseBatch = "99th FC",
      trainingGroup = "Alpha",
      otherDetails = "Met at LBSNAA",
    )
    val migrated = legacy.migrateLegacyTrainingFieldsToOtherDetails()
    assertEquals("", migrated.foundationCourseBatch)
    assertEquals("", migrated.trainingGroup)
    assertEquals(
      "Met at LBSNAA\nFoundation Course batch: 99th FC\nTraining group: Alpha",
      migrated.otherDetails,
    )
  }
}
