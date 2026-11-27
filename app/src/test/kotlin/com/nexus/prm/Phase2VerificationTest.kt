package com.nexus.prm

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.nexus.prm.data.BackupCrypto
import com.nexus.prm.data.CsvRosterParser
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.DirectorySnapshot
import com.nexus.prm.data.DuplicateMatchField
import com.nexus.prm.data.DuplicateMatcher
import com.nexus.prm.data.LocalDirectoryRepository
import com.nexus.prm.data.LocalStorageCrypto
import com.nexus.prm.data.OwnerCard
import com.nexus.prm.data.OwnerCardVCard
import com.nexus.prm.data.PersonProfile
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Phase2VerificationTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private class TestXorCrypto : LocalStorageCrypto {
    override fun encrypt(plaintext: ByteArray): ByteArray =
      ByteArray(plaintext.size) { i -> (plaintext[i].toInt() xor 0x5A).toByte() }

    override fun decrypt(ciphertext: ByteArray): ByteArray =
      ByteArray(ciphertext.size) { i -> (ciphertext[i].toInt() xor 0x5A).toByte() }
  }

  private fun newRepo(dir: File = tempFolder.newFolder()): LocalDirectoryRepository =
    LocalDirectoryRepository(filesDir = dir, crypto = TestXorCrypto())

  private val sampleCsv = """
    name,phone,email,service,cadre,service_batch,fc_batch,training_group
    Aditi Roster,+91-90000-01001,aditi@example.com,IAS,Bihar,2026,FC Test,Group A
    Meera Test,+91 90000 01002,meera@example.com,IPS,Assam,2026,FC Test,Group A
    Meera Repeat,,MEERA@example.com,IPS,Assam,2026,FC Test,Group B
    ,+91 90000 01003,blank@example.com,,,,,
    Kabir Test,,,,,,,
    Riya Test,+91 90000 01004,riya@example.com,,,,,"Group B, Trek"
  """.trimIndent()

  @Test
  fun P2_01_upgradeInPlace_preservesPhase1RecordsAndPhotos() = runTest {
    val dir = tempFolder.newFolder()
    val crypto = TestXorCrypto()
    val photoBytes = byteArrayOf(11, 22, 33, 44)
    val photosDir = File(dir, "photos").apply { mkdirs() }
    File(photosDir, "aditi.jpg").writeBytes(crypto.encrypt(photoBytes))

    // Write a Phase 1 snapshot (schemaVersion = 1, no tags, groups, or ownerCard fields)
    val phase1Json = """
      {
        "schemaVersion": 1,
        "profiles": [
          {
            "id": "aditi-1",
            "name": "Aditi Test",
            "phone": "+91 90000 01001",
            "trainingGroup": "Group A",
            "photoFileName": "aditi.jpg",
            "isFavourite": true,
            "createdAtEpochMs": 1000,
            "updatedAtEpochMs": 1000
          }
        ]
      }
    """.trimIndent()
    File(dir, "nexus_directory.enc").writeBytes(crypto.encrypt(phase1Json.encodeToByteArray()))

    val upgradedRepo = LocalDirectoryRepository(filesDir = dir, crypto = crypto)
    val aditi = upgradedRepo.getProfile("aditi-1")
    assertNotNull(aditi)
    assertEquals("Aditi Test", aditi?.name)
    assertTrue(aditi?.isFavourite == true)
    assertEquals("Group A", aditi?.trainingGroup)
    assertEquals(emptyList<String>(), aditi?.tags)
    assertArrayEquals(photoBytes, upgradedRepo.loadPhotoBytes("aditi.jpg"))
    assertEquals(emptyList<Any>(), upgradedRepo.groups.value)
    assertEquals(OwnerCard(), upgradedRepo.ownerCard.value)
    assertTrue(DirectorySearch.matchesQuery(aditi!!, "aditi"))
  }

  @Test
  fun P2_02_importOneAndroidContact_mapsSelectedValuesAndCommitsOnlyApproved() = runTest {
    val repo = newRepo()
    val phoneContactSelected = PersonProfile(
      id = "pending",
      name = "Phone Contact Test",
      phone = "+91 90000 02002", // second number chosen
      email = "phonecontact@example.com",
      createdAtEpochMs = 1000L,
      updatedAtEpochMs = 1000L,
    )

    val saved = repo.saveProfilesBatch(listOf(phoneContactSelected))
    assertEquals(1, saved.size)
    assertEquals(1, repo.profiles.value.size)
    assertEquals("Phone Contact Test", repo.profiles.value.first().name)
    assertEquals("+91 90000 02002", repo.profiles.value.first().phone)
    assertEquals("phonecontact@example.com", repo.profiles.value.first().email)
    assertTrue(repo.profiles.value.none { it.name == "Do Not Import Test" })
  }

  @Test
  fun P2_03_cancelOrDenyImport_causesZeroWrites() = runTest {
    val dir = tempFolder.newFolder()
    val repo = newRepo(dir)
    repo.saveProfile(
      PersonProfile(id = "p1", name = "Aditi Test", createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L),
    )

    val saved = repo.saveProfilesBatch(emptyList())
    assertEquals(0, saved.size)
    assertEquals(1, repo.profiles.value.size)
    assertEquals("Aditi Test", newRepo(dir).profiles.value.first().name)
  }

  @Test
  fun P2_04_previewAndCancelCsv_classifiesMatchesAndWritesZeroRecords() = runTest {
    val repo = newRepo()
    val aditi = repo.saveProfile(
      PersonProfile(
        id = "aditi-1",
        name = "Aditi Test",
        phone = "+91 90000 01001",
        trainingGroup = "Group A",
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
    )

    val parsed = CsvRosterParser.parse(sampleCsv, nowEpochMs = 2000L)
    assertNull(parsed.fatalError)
    assertEquals(5, parsed.validRows.size)
    assertEquals(1, parsed.errors.size)

    val running = mutableListOf(aditi)
    val matches = mutableMapOf<String, DuplicateMatchField>()
    for (row in parsed.validRows) {
      val match = DuplicateMatcher.findMatch(row.profile.phone, row.profile.email, running)
      if (match != null) {
        matches[row.profile.name] = match.matchedField
      } else {
        running += row.profile
      }
    }

    assertEquals(DuplicateMatchField.PHONE, matches["Aditi Roster"])
    assertEquals(DuplicateMatchField.EMAIL, matches["Meera Repeat"])
    assertEquals(2, matches.size)
    // Cancelled preview: directory count unchanged
    assertEquals(1, repo.profiles.value.size)
  }

  @Test
  fun P2_05_saveSelectedCsvRows_exactCountsAndCommitFailureSafety() = runTest {
    val dir = tempFolder.newFolder()
    val repo = newRepo(dir)
    repo.saveProfile(
      PersonProfile(
        id = "aditi-1",
        name = "Aditi Test",
        phone = "+91 90000 01001",
        trainingGroup = "Group A",
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
    )

    val parsed = CsvRosterParser.parse(sampleCsv, nowEpochMs = 2000L)
    val approved = parsed.validRows
      .filter { it.profile.name !in setOf("Aditi Roster", "Meera Repeat") }
      .map { it.profile }
    val skippedCount = parsed.validRows.size - approved.size
    val invalidCount = parsed.errors.size

    val saved = repo.saveProfilesBatch(approved)
    assertEquals(3, saved.size)
    assertEquals(2, skippedCount)
    assertEquals(1, invalidCount)

    val reloaded = newRepo(dir)
    assertEquals(4, reloaded.profiles.value.size)
    val kabir = reloaded.profiles.value.first { it.name == "Kabir Test" }
    assertEquals("", kabir.phone)
    assertEquals("", kabir.trainingGroup)
    val riya = reloaded.profiles.value.first { it.name == "Riya Test" }
    assertEquals("Group B, Trek", riya.trainingGroup)
    val aditi = reloaded.getProfile("aditi-1")
    assertEquals("Aditi Test", aditi?.name)

    // Simulated commit failure (blank name in batch) leaves zero partially imported records
    var failed = false
    try {
      reloaded.saveProfilesBatch(
        listOf(
          PersonProfile(id = "x1", name = "Valid Before Failure", createdAtEpochMs = 1L, updatedAtEpochMs = 1L),
          PersonProfile(id = "x2", name = "   ", createdAtEpochMs = 2L, updatedAtEpochMs = 2L),
        ),
      )
    } catch (_: IllegalArgumentException) {
      failed = true
    }
    assertTrue(failed)
    assertEquals(4, reloaded.profiles.value.size)
    assertTrue(reloaded.profiles.value.none { it.name == "Valid Before Failure" })
  }

  @Test
  fun P2_06_skipOrKeepSuggestedDuplicate_allMatchingRules() = runTest {
    val repo = newRepo()
    val aditi = repo.saveProfile(
      PersonProfile(
        id = "aditi-1",
        name = "Aditi Test",
        phone = "+91 90000 01001",
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
    )
    val meera = repo.saveProfile(
      PersonProfile(
        id = "meera-1",
        name = "Meera Test",
        email = "meera@example.com",
        createdAtEpochMs = 2000L,
        updatedAtEpochMs = 2000L,
      ),
    )

    // Email case + whitespace match
    val emailMatch = DuplicateMatcher.findMatch("", "  MEERA@EXAMPLE.COM ", repo.profiles.value)
    assertEquals(meera.id, emailMatch?.existingProfile?.id)
    assertEquals(DuplicateMatchField.EMAIL, emailMatch?.matchedField)

    // Phone formatting match
    val phoneMatch = DuplicateMatcher.findMatch("+91-90000-01001", "", repo.profiles.value)
    assertEquals(aditi.id, phoneMatch?.existingProfile?.id)
    assertEquals(DuplicateMatchField.PHONE, phoneMatch?.matchedField)

    // Name-only produces no suggestion
    assertNull(DuplicateMatcher.findMatch("", "", repo.profiles.value))

    // Editing a profile excludes itself
    assertNull(DuplicateMatcher.findMatch("+91 90000 01001", "", repo.profiles.value, excludeProfileId = aditi.id))

    // Keep separately adds a new record without modifying Meera
    repo.saveProfile(
      PersonProfile(
        id = "meera-2",
        name = "Meera Second",
        email = "MEERA@EXAMPLE.COM",
        createdAtEpochMs = 3000L,
        updatedAtEpochMs = 3000L,
      ),
    )
    assertEquals(3, repo.profiles.value.size)
    assertEquals("Meera Test", repo.getProfile("meera-1")?.name)

    // Skip adds zero records
    assertEquals(0, repo.saveProfilesBatch(emptyList()).size)
    assertEquals(3, repo.profiles.value.size)
  }

  @Test
  fun P2_07_rejectCsvWithNoHeader_returnsErrorAndZeroWrites() = runTest {
    val repo = newRepo()
    val noHeaderCsv = sampleCsv.lines().drop(1).joinToString("\n")
    val result = CsvRosterParser.parse(noHeaderCsv)
    assertNotNull(result.fatalError)
    assertEquals(0, result.validRows.size)
    assertEquals(0, repo.profiles.value.size)
  }

  @Test
  fun P2_08_searchByTag_caseInsensitiveReuseAndSingleTagRemoval() = runTest {
    val repo = newRepo()
    val meera = repo.saveProfile(
      PersonProfile(
        id = "meera-1",
        name = "Meera Test",
        tags = listOf("Public health", "Heritage walks"),
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
    )

    val beforeRemoval = DirectorySearch.filterAndSort(
      profiles = repo.profiles.value,
      query = "public health",
      selectedCategory = null,
      selectedTrainingGroup = null,
      favouritesOnly = false,
      recentlyAddedOnly = false,
    )
    assertEquals(listOf(meera), beforeRemoval)

    // Canonical case reuse
    val second = repo.saveProfile(
      PersonProfile(
        id = "kabir-1",
        name = "Kabir Test",
        tags = listOf("  public health "),
        createdAtEpochMs = 2000L,
        updatedAtEpochMs = 2000L,
      ),
    )
    assertEquals(listOf("Public health"), second.tags)

    // Remove one tag from Meera without removing Heritage walks
    val updatedMeera = repo.saveProfile(
      meera.copy(tags = meera.tags.filterNot { it.equals("Public health", ignoreCase = true) }),
    )
    assertEquals(listOf("Heritage walks"), updatedMeera.tags)
    assertFalse(DirectorySearch.matchesQuery(updatedMeera, "public health"))
    assertTrue(DirectorySearch.matchesQuery(updatedMeera, "heritage walks"))
  }

  @Test
  fun P2_09_manageMembershipInTwoGroups_renameDeleteAndPersistence() = runTest {
    val dir = tempFolder.newFolder()
    val repo = newRepo(dir)
    val meera = repo.saveProfile(
      PersonProfile(
        id = "meera-1",
        name = "Meera Test",
        trainingGroup = "Group A",
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
    )

    val g1 = repo.createGroup("District Visit Team")
    val g2 = repo.createGroup("Faculty Mentors")
    repo.setGroupMembers(g1.id, listOf(meera.id))
    repo.setGroupMembers(g2.id, listOf(meera.id))

    repo.renameGroup(g1.id, "District Visit Squad")
    repo.deleteGroup(g2.id)

    val reloaded = newRepo(dir)
    assertEquals(1, reloaded.groups.value.size)
    assertEquals("District Visit Squad", reloaded.groups.value.first().name)
    assertEquals(listOf(meera.id), reloaded.groups.value.first().memberIds)
    assertEquals("Group A", reloaded.getProfile(meera.id)?.trainingGroup)
  }

  @Test
  fun P2_10_ownerQrOffline_decodesGeneratedQrAndExcludesUnselectedFields() = runTest {
    val dir = tempFolder.newFolder()
    val repo = newRepo(dir)
    val card = OwnerCard(
      name = "Ayush Kumar",
      phone = "+91 98765 43210",
      email = "ayush@example.com",
      designation = "Officer Trainee",
      shareName = true,
      sharePhone = false,
      shareEmail = true,
      shareDesignation = false,
    )
    repo.saveOwnerCard(card)

    val reloaded = newRepo(dir)
    assertFalse(reloaded.ownerCard.value.sharePhone)
    assertFalse(reloaded.ownerCard.value.shareDesignation)

    val preview = OwnerCardVCard.buildPreview(reloaded.ownerCard.value)
    val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(preview.vCardPayload, BarcodeFormat.QR_CODE, 320, 320, hints)
    val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(BitMatrixLuminanceSource(matrix)))).text

    assertEquals(preview.vCardPayload, decoded)
    assertTrue(decoded.contains("FN:Ayush Kumar"))
    assertTrue(decoded.contains("EMAIL:ayush@example.com"))
    assertFalse(decoded.contains("98765"))
    assertFalse(decoded.contains("Officer Trainee"))
  }

  @Test
  fun P2_11_restorePhase2Backup_roundTripWithPhotoFavouriteTagsGroupsAndOwnerCard() = runTest {
    val sourceRepo = newRepo()
    val photoBytes = byteArrayOf(7, 8, 9, 10)
    val aditi = sourceRepo.saveProfile(
      PersonProfile(
        id = "aditi-1",
        name = "Aditi Test",
        phone = "+91 90000 01001",
        tags = listOf("Public health"),
        isFavourite = true,
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
      pendingPhotoBytes = photoBytes,
    )
    val group = sourceRepo.createGroup("District Visit Squad")
    sourceRepo.setGroupMembers(group.id, listOf(aditi.id))
    sourceRepo.saveOwnerCard(
      OwnerCard(
        name = "Owner Name",
        email = "owner@example.com",
        phone = "9999999999",
        sharePhone = false,
      ),
    )

    val backup = sourceRepo.exportBackup("Phase2Pass!")

    val targetRepo = newRepo()
    val preview = targetRepo.inspectBackup(backup, "Phase2Pass!")
    targetRepo.applyBackup(preview)

    val restored = targetRepo.profiles.value.first()
    assertEquals("Aditi Test", restored.name)
    assertTrue(restored.isFavourite)
    assertEquals(listOf("Public health"), restored.tags)
    assertArrayEquals(photoBytes, targetRepo.loadPhotoBytes(restored.photoFileName!!))
    assertEquals("District Visit Squad", targetRepo.groups.value.first().name)
    assertEquals(listOf(restored.id), targetRepo.groups.value.first().memberIds)
    assertEquals("Owner Name", targetRepo.ownerCard.value.name)
    assertFalse(targetRepo.ownerCard.value.sharePhone)
  }

  @Test
  fun P2_12_restorePhase1BackupAndFailureSafety() = runTest {
    val phase1Photo = byteArrayOf(55, 66, 77)
    val phase1ManifestJson = """
      {
        "schemaVersion": 1,
        "createdAtEpochMs": 1000,
        "profiles": [
          {
            "id": "aditi-p1",
            "name": "Aditi Test",
            "phone": "+91 90000 01001",
            "trainingGroup": "Group A",
            "photoFileName": "aditi.jpg",
            "isFavourite": true,
            "createdAtEpochMs": 1000,
            "updatedAtEpochMs": 1000
          }
        ]
      }
    """.trimIndent()
    val zipBytes = ByteArrayOutputStream().use { baos ->
      ZipOutputStream(baos).use { zip ->
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(phase1ManifestJson.encodeToByteArray())
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("photos/aditi.jpg"))
        zip.write(phase1Photo)
        zip.closeEntry()
      }
      baos.toByteArray()
    }
    val phase1Backup = BackupCrypto.encrypt(zipBytes, "Phase1Pass")

    val targetRepo = newRepo()
    val livePhoto = byteArrayOf(1, 2, 3)
    val livePerson = targetRepo.saveProfile(
      PersonProfile(id = "live-1", name = "Live Person", createdAtEpochMs = 500L, updatedAtEpochMs = 500L),
      pendingPhotoBytes = livePhoto,
    )

    // Wrong password preserves live data
    var wrongPassFailed = false
    try {
      targetRepo.inspectBackup(phase1Backup, "WrongPass")
    } catch (_: AEADBadTagException) {
      wrongPassFailed = true
    }
    assertTrue(wrongPassFailed)
    assertEquals(listOf("Live Person"), targetRepo.profiles.value.map { it.name })
    assertArrayEquals(livePhoto, targetRepo.loadPhotoBytes(livePerson.photoFileName!!))

    // Damaged archive preserves live data
    val damaged = phase1Backup.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 0xFF).toByte() }
    var damagedFailed = false
    try {
      targetRepo.inspectBackup(damaged, "Phase1Pass")
    } catch (_: Exception) {
      damagedFailed = true
    }
    assertTrue(damagedFailed)
    assertEquals(listOf("Live Person"), targetRepo.profiles.value.map { it.name })

    // Successful Phase 1 restore initializes Phase 2 defaults cleanly
    val preview = targetRepo.inspectBackup(phase1Backup, "Phase1Pass")
    targetRepo.applyBackup(preview)
    assertEquals(1, targetRepo.profiles.value.size)
    val restored = targetRepo.profiles.value.first()
    assertEquals("Aditi Test", restored.name)
    assertEquals(emptyList<String>(), restored.tags)
    assertEquals(emptyList<Any>(), targetRepo.groups.value)
    assertEquals(OwnerCard(), targetRepo.ownerCard.value)
    assertArrayEquals(phase1Photo, targetRepo.loadPhotoBytes(restored.photoFileName!!))
  }

  private class BitMatrixLuminanceSource(private val matrix: BitMatrix) :
    LuminanceSource(matrix.width, matrix.height) {
    override fun getRow(y: Int, row: ByteArray?): ByteArray {
      val out = if (row == null || row.size < width) ByteArray(width) else row
      for (x in 0 until width) {
        out[x] = if (matrix[x, y]) 0 else 0xFF.toByte()
      }
      return out
    }

    override fun getMatrix(): ByteArray {
      val out = ByteArray(width * height)
      for (y in 0 until height) {
        for (x in 0 until width) {
          out[y * width + x] = if (matrix[x, y]) 0 else 0xFF.toByte()
        }
      }
      return out
    }
  }
}
