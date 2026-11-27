package com.nexus.prm.data

import java.io.File
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalDirectoryRepositoryTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private class TestXorCrypto : LocalStorageCrypto {
    override fun encrypt(plaintext: ByteArray): ByteArray =
      ByteArray(plaintext.size) { i -> (plaintext[i].toInt() xor 0x5A).toByte() }

    override fun decrypt(ciphertext: ByteArray): ByteArray =
      ByteArray(ciphertext.size) { i -> (ciphertext[i].toInt() xor 0x5A).toByte() }
  }

  private fun newRepo(dir: File = tempFolder.newFolder()): LocalDirectoryRepository =
    LocalDirectoryRepository(filesDir = dir, crypto = TestXorCrypto())

  @Test
  fun saveProfile_allowsNameOnlyAndKeepsSameNameSeparateAndPersistsAcrossRestart() = runTest {
    val dir = tempFolder.newFolder()
    val repo1 = newRepo(dir)

    val first = repo1.saveProfile(
      PersonProfile(
        id = "id-1",
        name = "  Rohan Verma  ",
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
    )
    val second = repo1.saveProfile(
      PersonProfile(
        id = "id-2",
        name = "Rohan Verma",
        whereWeMet = "Library",
        createdAtEpochMs = 2000L,
        updatedAtEpochMs = 2000L,
      ),
    )

    assertEquals("Rohan Verma", first.name)
    assertEquals(2, repo1.profiles.value.size)

    val repo2 = newRepo(dir)
    assertEquals(2, repo2.profiles.value.size)
    assertEquals("Library", repo2.getProfile(second.id)?.whereWeMet)
  }

  @Test(expected = IllegalArgumentException::class)
  fun saveProfile_rejectsBlankName() = runTest {
    val repo = newRepo()
    repo.saveProfile(
      PersonProfile(
        id = "id-blank",
        name = "   ",
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
    )
  }

  @Test
  fun photoLifecycle_saveReplaceAndRemove() = runTest {
    val dir = tempFolder.newFolder()
    val repo = newRepo(dir)
    val photo1 = byteArrayOf(1, 2, 3, 4)
    val photo2 = byteArrayOf(5, 6, 7, 8)

    val saved1 = repo.saveProfile(
      profile = PersonProfile(
        id = "p1",
        name = "Meera",
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
      pendingPhotoBytes = photo1,
    )
    val file1 = saved1.photoFileName
    assertNotNull(file1)
    assertArrayEquals(photo1, repo.loadPhotoBytes(file1!!))

    val saved2 = repo.saveProfile(saved1, pendingPhotoBytes = photo2)
    val file2 = saved2.photoFileName
    assertNotNull(file2)
    assertTrue(file1 != file2)
    assertNull(repo.loadPhotoBytes(file1))
    assertArrayEquals(photo2, repo.loadPhotoBytes(file2!!))

    val saved3 = repo.saveProfile(saved2, removePhoto = true)
    assertNull(saved3.photoFileName)
    assertNull(repo.loadPhotoBytes(file2))
  }

  @Test
  fun backupAndRestore_roundTripAndFailurePathsPreserveLiveData() = runTest {
    val sourceDir = tempFolder.newFolder("source")
    val targetDir = tempFolder.newFolder("target")
    val sourceRepo = newRepo(sourceDir)
    val photoBytes = byteArrayOf(10, 20, 30, 40, 50)

    val saved = sourceRepo.saveProfile(
      profile = PersonProfile(
        id = "p1",
        name = "Ananya Iyer",
        phone = "9876543210",
        category = PersonCategory.SENIOR_OFFICER,
        whereWeMet = "Director's address",
        isFavourite = true,
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
      ),
      pendingPhotoBytes = photoBytes,
    )

    val backupBytes = sourceRepo.exportBackup("StrongPass123")

    val targetRepo = newRepo(targetDir)
    val existing = targetRepo.saveProfile(
      PersonProfile(
        id = "existing-1",
        name = "Existing Person",
        createdAtEpochMs = 500L,
        updatedAtEpochMs = 500L,
      ),
    )

    // Wrong password must fail without modifying targetRepo
    var wrongPasswordFailed = false
    try {
      targetRepo.inspectBackup(backupBytes, "WrongPass")
    } catch (_: AEADBadTagException) {
      wrongPasswordFailed = true
    }
    assertTrue(wrongPasswordFailed)
    assertEquals(listOf(existing), targetRepo.profiles.value)

    // Damaged file must fail without modifying targetRepo
    val damaged = backupBytes.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 0xFF).toByte() }
    var damagedFailed = false
    try {
      targetRepo.inspectBackup(damaged, "StrongPass123")
    } catch (_: Exception) {
      damagedFailed = true
    }
    assertTrue(damagedFailed)
    assertEquals(listOf(existing), targetRepo.profiles.value)

    // Valid restore replaces live data and restores photos and favourites
    val preview = targetRepo.inspectBackup(backupBytes, "StrongPass123")
    assertEquals(1, preview.profiles.size)
    targetRepo.applyBackup(preview)

    val restored = targetRepo.profiles.value
    assertEquals(1, restored.size)
    assertEquals(saved.name, restored.first().name)
    assertTrue(restored.first().isFavourite)
    assertArrayEquals(photoBytes, targetRepo.loadPhotoBytes(restored.first().photoFileName!!))
  }

  @Test
  fun groupsAndOwnerCard_andPhase1BackupCompatibility() = runTest {
    val repo = newRepo()
    val p1 = repo.saveProfile(
      PersonProfile(
        id = "p1",
        name = "Priya Nair",
        tags = listOf("Public Health"),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
    )

    val g1 = repo.createGroup("Mess Table 4")
    val g2 = repo.createGroup("Syndicate")
    repo.setGroupMembers(g1.id, listOf(p1.id))
    repo.setGroupMembers(g2.id, listOf(p1.id))
    assertEquals(2, repo.groups.value.size)

    repo.deleteGroup(g1.id)
    assertEquals(1, repo.groups.value.size)
    assertEquals(1, repo.profiles.value.size)

    repo.saveOwnerCard(OwnerCard(name = "Owner Name", service = "IAS", shareService = false))
    val backup = repo.exportBackup("Pass123!")

    val target = newRepo()
    val preview = target.inspectBackup(backup, "Pass123!")
    target.applyBackup(preview)
    assertEquals(1, target.profiles.value.size)
    assertEquals(listOf("Public Health"), target.profiles.value.first().tags)
    assertEquals(1, target.groups.value.size)
    assertEquals("Owner Name", target.ownerCard.value.name)
    assertEquals(false, target.ownerCard.value.shareService)
  }
}
