package com.nexus.prm

import com.nexus.prm.data.BackupCrypto
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.LocalDirectoryRepository
import com.nexus.prm.data.LocalStorageCrypto
import com.nexus.prm.data.OwnerCard
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.RestoreMerger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Phase21VerificationTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private class TestXorCrypto(var failOnEncryptCallNumber: Int? = null) : LocalStorageCrypto {
    private var encryptCalls = 0
    override fun encrypt(plaintext: ByteArray): ByteArray {
      encryptCalls++
      if (failOnEncryptCallNumber != null && encryptCalls >= failOnEncryptCallNumber!!) {
        throw IOException("Simulated disk/crypto failure")
      }
      return ByteArray(plaintext.size) { i -> (plaintext[i].toInt() xor 0x5A).toByte() }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray =
      ByteArray(ciphertext.size) { i -> (ciphertext[i].toInt() xor 0x5A).toByte() }
  }

  private fun newRepo(
    dir: File = tempFolder.newFolder(),
    crypto: LocalStorageCrypto = TestXorCrypto(),
  ): LocalDirectoryRepository = LocalDirectoryRepository(filesDir = dir, crypto = crypto)

  @Test
  fun P2_1_01_defaultMerge_preservesLocalOnlyAndAddsBackupOnly() = runTest {
    val sourceRepo = newRepo()
    sourceRepo.saveProfile(
      PersonProfile(id = "backup-1", name = "Backup Only", phone = "9111111111", createdAtEpochMs = 100L, updatedAtEpochMs = 100L),
    )
    val backup = sourceRepo.exportBackup("Pass123!")

    val targetRepo = newRepo()
    targetRepo.saveProfile(
      PersonProfile(id = "local-1", name = "Local Only", phone = "9222222222", createdAtEpochMs = 200L, updatedAtEpochMs = 200L),
    )

    val preview = targetRepo.inspectBackup(backup, "Pass123!")
    val mergePreview = RestoreMerger.buildPreview(targetRepo.profiles.value, preview)
    assertEquals(1, mergePreview.toAdd.size)
    assertEquals(1, mergePreview.localOnlyCount)
    assertEquals(0, mergePreview.duplicates.size)

    targetRepo.applyMergeRestore(preview)
    val names = targetRepo.profiles.value.map { it.name }.toSet()
    assertEquals(setOf("Local Only", "Backup Only"), names)
  }

  @Test
  fun P2_1_02_sharedId_keepsLocalValuesAndPhoto_fillsEmptyFields() = runTest {
    val sourceRepo = newRepo()
    val backupPhoto = byteArrayOf(9, 8, 7)
    sourceRepo.saveProfile(
      PersonProfile(
        id = "shared-1",
        name = "Backup Name",
        phone = "+91 90000 00001",
        email = "shared@example.com",
        organisation = "LBSNAA",
        trainingGroup = "Backup Group",
        isFavourite = false,
        tags = listOf("BackupTag"),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
      pendingPhotoBytes = backupPhoto,
    )
    val backup = sourceRepo.exportBackup("Pass123!")

    val targetRepo = newRepo()
    val localPhoto = byteArrayOf(1, 2, 3)
    targetRepo.saveProfile(
      PersonProfile(
        id = "shared-1",
        name = "Local Name",
        phone = "+91 90000 00002",
        email = "", // blank locally -> should be filled from backup
        organisation = "", // blank locally -> should be filled from backup
        trainingGroup = "Local Group",
        isFavourite = true,
        tags = listOf("LocalTag"),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 200L,
      ),
      pendingPhotoBytes = localPhoto,
    )

    val preview = targetRepo.inspectBackup(backup, "Pass123!")
    targetRepo.applyMergeRestore(preview)

    assertEquals(1, targetRepo.profiles.value.size)
    val merged = targetRepo.getProfile("shared-1")!!
    assertEquals("Local Name", merged.name)
    assertEquals("+91 90000 00002", merged.phone)
    assertEquals("", merged.trainingGroup)
    assertTrue(merged.otherDetails.contains("Training group: Local Group"))
    assertTrue(merged.isFavourite)
    assertEquals("shared@example.com", merged.email)
    assertEquals("LBSNAA", merged.organisation)
    assertEquals(listOf("LocalTag", "BackupTag"), merged.tags)
    assertArrayEquals(localPhoto, targetRepo.loadPhotoBytes(merged.photoFileName!!))
  }

  @Test
  fun P2_1_03_duplicateReviewAndRetry_skipAndKeepSeparately_idempotentRetry() = runTest {
    val sourceRepo = newRepo()
    val pSkip = sourceRepo.saveProfile(
      PersonProfile(id = "inc-skip", name = "Incoming Skip", phone = "+91 90000 01001", createdAtEpochMs = 100L, updatedAtEpochMs = 100L),
    )
    val pKeep = sourceRepo.saveProfile(
      PersonProfile(id = "inc-keep", name = "Incoming Keep", email = "KEEP@EXAMPLE.COM", createdAtEpochMs = 200L, updatedAtEpochMs = 200L),
    )
    val group = sourceRepo.createGroup("Syndicate")
    sourceRepo.setGroupMembers(group.id, listOf(pSkip.id, pKeep.id))
    val backup = sourceRepo.exportBackup("Pass123!")

    val targetRepo = newRepo()
    targetRepo.saveProfile(
      PersonProfile(id = "loc-1", name = "Local Phone Match", phone = "+91-90000-01001", createdAtEpochMs = 50L, updatedAtEpochMs = 50L),
    )
    targetRepo.saveProfile(
      PersonProfile(id = "loc-2", name = "Local Email Match", email = "keep@example.com", createdAtEpochMs = 60L, updatedAtEpochMs = 60L),
    )

    val preview1 = targetRepo.inspectBackup(backup, "Pass123!")
    val mergePreview1 = RestoreMerger.buildPreview(targetRepo.profiles.value, preview1)
    assertEquals(2, mergePreview1.duplicates.size)

    // Skip inc-skip, keep inc-keep separately
    targetRepo.applyMergeRestore(preview1, keepDuplicateIds = setOf("inc-keep"))
    assertEquals(3, targetRepo.profiles.value.size)
    assertNotNull(targetRepo.getProfile("inc-keep"))
    assertEquals(listOf("inc-keep"), targetRepo.groups.value.first().memberIds)

    // Retry restoring the exact same archive: inc-keep is now matched by stable ID, inc-skip still skipped
    val preview2 = targetRepo.inspectBackup(backup, "Pass123!")
    val mergePreview2 = RestoreMerger.buildPreview(targetRepo.profiles.value, preview2)
    assertEquals(1, mergePreview2.matchedById.size)
    assertEquals(1, mergePreview2.duplicates.size)
    targetRepo.applyMergeRestore(preview2, keepDuplicateIds = emptySet())
    assertEquals(3, targetRepo.profiles.value.size)
    assertEquals(listOf("inc-keep"), targetRepo.groups.value.first().memberIds)
  }

  @Test
  fun P2_1_04_olderBackups_mergePhase1PreservesPhase2Data_andPhase2CleanInstall() = runTest {
    // Phase 1 archive
    val phase1Json = """
      {
        "schemaVersion": 1,
        "createdAtEpochMs": 1000,
        "profiles": [
          {
            "id": "p1-aditi",
            "name": "Aditi Phase 1",
            "phone": "+91 90000 01001",
            "trainingGroup": "Group A",
            "createdAtEpochMs": 1000,
            "updatedAtEpochMs": 1000
          }
        ]
      }
    """.trimIndent()
    val zipBytes = ByteArrayOutputStream().use { baos ->
      ZipOutputStream(baos).use { zip ->
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(phase1Json.encodeToByteArray())
        zip.closeEntry()
      }
      baos.toByteArray()
    }
    val phase1Backup = BackupCrypto.encrypt(zipBytes, "OldPass")

    val populated = newRepo()
    val localMeera = populated.saveProfile(
      PersonProfile(id = "loc-meera", name = "Meera Local", tags = listOf("Public health"), createdAtEpochMs = 100L, updatedAtEpochMs = 100L),
    )
    val localGroup = populated.createGroup("District Visit Squad")
    populated.setGroupMembers(localGroup.id, listOf(localMeera.id))
    populated.saveOwnerCard(OwnerCard(name = "Local Owner", email = "owner@example.com", shareEmail = false))

    val p1Preview = populated.inspectBackup(phase1Backup, "OldPass")
    populated.applyMergeRestore(p1Preview)

    assertEquals(2, populated.profiles.value.size)
    assertEquals(listOf("Public health"), populated.getProfile("loc-meera")?.tags)
    assertEquals(listOf(localMeera.id), populated.groups.value.first().memberIds)
    assertEquals("Local Owner", populated.ownerCard.value.name)
    assertFalse(populated.ownerCard.value.shareEmail)

    // Clean install merge of Phase 2 backup restores photo, memberships, and owner card
    val p2Backup = populated.exportBackup("P2Pass")
    val cleanInstall = newRepo()
    val p2Preview = cleanInstall.inspectBackup(p2Backup, "P2Pass")
    cleanInstall.applyMergeRestore(p2Preview)
    assertEquals(2, cleanInstall.profiles.value.size)
    assertEquals(1, cleanInstall.groups.value.size)
    assertEquals("Local Owner", cleanInstall.ownerCard.value.name)
    assertFalse(cleanInstall.ownerCard.value.shareEmail)
  }

  @Test
  fun P2_1_05_fullReplacement_replacesAllData() = runTest {
    val sourceRepo = newRepo()
    sourceRepo.saveProfile(
      PersonProfile(id = "b1", name = "Backup Person", tags = listOf("BackupTag"), createdAtEpochMs = 100L, updatedAtEpochMs = 100L),
    )
    sourceRepo.createGroup("Backup Group")
    sourceRepo.saveOwnerCard(OwnerCard(name = "Backup Owner"))
    val backup = sourceRepo.exportBackup("Pass123!")

    val targetRepo = newRepo()
    targetRepo.saveProfile(
      PersonProfile(id = "l1", name = "Local Person", createdAtEpochMs = 200L, updatedAtEpochMs = 200L),
    )
    targetRepo.createGroup("Local Group")
    targetRepo.saveOwnerCard(OwnerCard(name = "Local Owner"))

    val preview = targetRepo.inspectBackup(backup, "Pass123!")
    targetRepo.applyBackup(preview)

    assertEquals(listOf("Backup Person"), targetRepo.profiles.value.map { it.name })
    assertEquals(listOf("Backup Group"), targetRepo.groups.value.map { it.name })
    assertEquals("Backup Owner", targetRepo.ownerCard.value.name)
  }

  @Test
  fun P2_1_06_failureSafety_wrongPasswordDamagedArchiveAndApplyFailure() = runTest {
    val sourceRepo = newRepo()
    sourceRepo.saveProfile(
      PersonProfile(id = "b1", name = "Incoming", createdAtEpochMs = 100L, updatedAtEpochMs = 100L),
      pendingPhotoBytes = byteArrayOf(9, 9, 9),
    )
    val backup = sourceRepo.exportBackup("Pass123!")

    val crypto = TestXorCrypto()
    val targetDir = tempFolder.newFolder()
    val targetRepo = newRepo(targetDir, crypto)
    val livePhoto = byteArrayOf(1, 2, 3)
    val live = targetRepo.saveProfile(
      PersonProfile(id = "l1", name = "Live Person", createdAtEpochMs = 200L, updatedAtEpochMs = 200L),
      pendingPhotoBytes = livePhoto,
    )
    val grp = targetRepo.createGroup("Live Group")
    targetRepo.setGroupMembers(grp.id, listOf(live.id))
    targetRepo.saveOwnerCard(OwnerCard(name = "Live Owner"))

    // Wrong password
    var wrongPass = false
    try {
      targetRepo.inspectBackup(backup, "Wrong")
    } catch (_: AEADBadTagException) {
      wrongPass = true
    }
    assertTrue(wrongPass)

    // Damaged archive
    val damaged = backup.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 0xFF).toByte() }
    var damagedErr = false
    try {
      targetRepo.inspectBackup(damaged, "Pass123!")
    } catch (_: Exception) {
      damagedErr = true
    }
    assertTrue(damagedErr)

    // Simulated apply failure during persistSnapshot (1st encrypt = staged photo, 2nd encrypt = snapshot)
    val preview = targetRepo.inspectBackup(backup, "Pass123!")
    crypto.failOnEncryptCallNumber = 2 // next encrypt is snapshot write
    var applyFailed = false
    try {
      targetRepo.applyMergeRestore(preview)
    } catch (_: IOException) {
      applyFailed = true
    }
    assertTrue(applyFailed)

    // Verify live state and photos remain intact
    assertEquals(listOf("Live Person"), targetRepo.profiles.value.map { it.name })
    assertEquals(listOf("Live Group"), targetRepo.groups.value.map { it.name })
    assertEquals("Live Owner", targetRepo.ownerCard.value.name)
    assertArrayEquals(livePhoto, targetRepo.loadPhotoBytes(live.photoFileName!!))
  }
}
