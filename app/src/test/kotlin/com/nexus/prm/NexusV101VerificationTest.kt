package com.nexus.prm

import com.nexus.prm.data.AutoBackupRunner
import com.nexus.prm.data.BackupDestination
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.LocalDirectoryRepository
import com.nexus.prm.data.LocalStorageCrypto
import com.nexus.prm.data.OwnerCard
import com.nexus.prm.data.PersonCategory
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.PostingEntry
import com.nexus.prm.data.ScheduledBackupFile
import com.nexus.prm.ui.profile.DraftPhotoStorage
import com.nexus.prm.ui.profile.PersonEditDraft
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NexusV101VerificationTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val passthroughCrypto = object : LocalStorageCrypto {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext
  }

  private fun newRepo(dir: File = tempFolder.newFolder("repo_${System.nanoTime()}")): LocalDirectoryRepository =
    LocalDirectoryRepository(filesDir = dir, crypto = passthroughCrypto)

  @Test
  fun NX101_01_consistentExportMissingPhotoFailureAndValidManualPreview() = runTest {
    val dir = tempFolder.newFolder("nx101_01")
    val repo = newRepo(dir)
    val initialBytes = byteArrayOf(10, 20, 30, 40)
    val saved = repo.saveProfile(
      profile = PersonProfile(
        id = "p-1",
        name = "Aditi Export",
        phone = "+91 90000 01001",
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
      pendingPhotoBytes = initialBytes,
    )

    // Valid manual export + reopen + inspect preview
    val exported = repo.exportBackup("ManualPass1!")
    val preview = repo.inspectBackup(exported, "ManualPass1!")
    assertEquals(1, preview.profiles.size)
    assertArrayEquals(initialBytes, preview.photos[saved.photoFileName])

    // Missing referenced photo must fail with clear message
    val photoFile = File(File(dir, "photos"), saved.photoFileName!!)
    assertTrue(photoFile.delete())
    var failureMessage = ""
    try {
      repo.exportBackup("ManualPass1!")
    } catch (e: IllegalStateException) {
      failureMessage = e.message.orEmpty()
    }
    assertTrue(failureMessage.contains("Missing photo"))
    assertTrue(failureMessage.contains("Aditi Export"))
  }

  @Test
  fun NX101_02_restoreCommitFailureWithOverlappingPhotosPreservesLiveDirectoryAndAllowsSuccess() = runTest {
    val sourceRepo = newRepo()
    val incomingPhotoBytes = byteArrayOf(99, 98, 97, 96)
    val srcSaved = sourceRepo.saveProfile(
      profile = PersonProfile(
        id = "incoming-1",
        name = "Incoming Person",
        phone = "+91 90000 02002",
        createdAtEpochMs = 200L,
        updatedAtEpochMs = 200L,
      ),
      pendingPhotoBytes = incomingPhotoBytes,
    )
    val backupBytes = sourceRepo.exportBackup("BackupPass!")

    val targetDir = tempFolder.newFolder("nx101_02_target")
    val targetRepo = newRepo(targetDir)
    val livePhotoBytes = byteArrayOf(1, 2, 3, 4, 5)
    val liveProfile = targetRepo.saveProfile(
      profile = PersonProfile(
        id = "local-1",
        name = "Local Live",
        phone = "+91 90000 01001",
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
      pendingPhotoBytes = livePhotoBytes,
    )

    // Force an overlapping photo filename in the preview
    val overlappingName = liveProfile.photoFileName!!
    val rawPreview = targetRepo.inspectBackup(backupBytes, "BackupPass!")
    val overlappingPreview = rawPreview.copy(
      profiles = rawPreview.profiles.map { it.copy(photoFileName = overlappingName) },
      photos = mapOf(overlappingName to incomingPhotoBytes),
    )

    // 1. Inject pre-commit failure during merge restore
    targetRepo.beforeSnapshotPersistHook = { throw IOException("Simulated disk full") }
    var mergeFailed = false
    try {
      targetRepo.applyMergeRestore(overlappingPreview)
    } catch (_: IOException) {
      mergeFailed = true
    }
    assertTrue(mergeFailed)

    // 2. Inject failure AFTER writing candidate snapshot has begun and BEFORE commit completes
    targetRepo.beforeSnapshotPersistHook = null
    targetRepo.afterSnapshotWriteHook = { throw IOException("Simulated mid-commit crash") }
    var midCommitMergeFailed = false
    try {
      targetRepo.applyMergeRestore(overlappingPreview)
    } catch (_: IOException) {
      midCommitMergeFailed = true
    }
    assertTrue(midCommitMergeFailed)

    var replaceFailed = false
    try {
      targetRepo.applyBackup(overlappingPreview)
    } catch (_: IOException) {
      replaceFailed = true
    }
    assertTrue(replaceFailed)
    targetRepo.afterSnapshotWriteHook = null
    assertFalse(File(targetDir, "nexus_directory.enc.tmp").exists())

    // Reopen repository from disk and verify records + live photo contents are intact
    val reopened = LocalDirectoryRepository(filesDir = targetDir, crypto = passthroughCrypto)
    assertEquals(1, reopened.profiles.value.size)
    assertEquals("Local Live", reopened.profiles.value.single().name)
    assertArrayEquals(livePhotoBytes, reopened.loadPhotoBytes(overlappingName))
    assertEquals(1, File(targetDir, "photos").listFiles()?.size)

    // 3. Exercise successful merge: local profile keeps original photo, incoming gets backup photo
    reopened.applyMergeRestore(overlappingPreview)
    val reopenedAfterMerge = LocalDirectoryRepository(filesDir = targetDir, crypto = passthroughCrypto)
    assertEquals(2, reopenedAfterMerge.profiles.value.size)
    val localAfterMerge = reopenedAfterMerge.getProfile("local-1")!!
    val incomingAfterMerge = reopenedAfterMerge.getProfile("incoming-1")!!
    assertEquals(overlappingName, localAfterMerge.photoFileName)
    assertArrayEquals(livePhotoBytes, reopenedAfterMerge.loadPhotoBytes(localAfterMerge.photoFileName!!))
    assertArrayEquals(incomingPhotoBytes, reopenedAfterMerge.loadPhotoBytes(incomingAfterMerge.photoFileName!!))

    // Repeating the merge keeps both photo references and bytes stable
    reopenedAfterMerge.applyMergeRestore(overlappingPreview)
    val reopenedAfterRepeat = LocalDirectoryRepository(filesDir = targetDir, crypto = passthroughCrypto)
    assertEquals(localAfterMerge.photoFileName, reopenedAfterRepeat.getProfile("local-1")!!.photoFileName)
    assertEquals(incomingAfterMerge.photoFileName, reopenedAfterRepeat.getProfile("incoming-1")!!.photoFileName)
    assertArrayEquals(livePhotoBytes, reopenedAfterRepeat.loadPhotoBytes(localAfterMerge.photoFileName!!))
    assertArrayEquals(incomingPhotoBytes, reopenedAfterRepeat.loadPhotoBytes(incomingAfterMerge.photoFileName!!))

    // 4. Exercise deliberate full replacement
    reopenedAfterRepeat.applyBackup(overlappingPreview)
    assertEquals(1, reopenedAfterRepeat.profiles.value.size)
    assertEquals("Incoming Person", reopenedAfterRepeat.profiles.value.single().name)
    assertArrayEquals(incomingPhotoBytes, reopenedAfterRepeat.loadPhotoBytes(reopenedAfterRepeat.profiles.value.single().photoFileName!!))
  }

  @Test
  fun NX101_02B_renameReturningFalseAfterCandidateWritePreservesAllStateAndAllowsRetry() = runTest {
    val sourceRepo = newRepo()
    val incomingPhotoBytes = byteArrayOf(88, 77, 66, 55)
    sourceRepo.saveProfile(
      profile = PersonProfile(
        id = "incoming-2",
        name = "Incoming Rename Test",
        phone = "+91 90000 03003",
        createdAtEpochMs = 200L,
        updatedAtEpochMs = 200L,
      ),
      pendingPhotoBytes = incomingPhotoBytes,
    )
    val backupBytes = sourceRepo.exportBackup("RenamePass!")

    val targetDir = tempFolder.newFolder("nx101_02b_target")
    val targetRepo = newRepo(targetDir)
    val livePhotoBytes = byteArrayOf(11, 22, 33, 44)
    val liveProfile = targetRepo.saveProfile(
      profile = PersonProfile(
        id = "local-2",
        name = "Local Original",
        phone = "+91 90000 01001",
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
      pendingPhotoBytes = livePhotoBytes,
    )
    val liveGroup = targetRepo.createGroup("Core Group")
    targetRepo.setGroupMembers(liveGroup.id, listOf(liveProfile.id))
    targetRepo.saveOwnerCard(OwnerCard(name = "Ayush Kumar", email = "ayush@example.com"))

    val preview = targetRepo.inspectBackup(backupBytes, "RenamePass!")
    var candidateExistedBeforeRename = false
    targetRepo.commitRenamer = { source, destination ->
      if (destination.name == "nexus_directory.enc") {
        candidateExistedBeforeRename = source.exists() && source.length() > 0L
        false
      } else {
        source.renameTo(destination)
      }
    }

    // 1. Merge fails when final rename returns false
    var mergeFailed = false
    try {
      targetRepo.applyMergeRestore(preview)
    } catch (_: IOException) {
      mergeFailed = true
    }
    assertTrue(mergeFailed)
    assertTrue(candidateExistedBeforeRename)
    assertFalse(File(targetDir, "nexus_directory.enc.tmp").exists())
    assertEquals(1, File(targetDir, "photos").listFiles()?.size)

    // 2. Full replacement fails when final rename returns false
    candidateExistedBeforeRename = false
    var replaceFailed = false
    try {
      targetRepo.applyBackup(preview)
    } catch (_: IOException) {
      replaceFailed = true
    }
    assertTrue(replaceFailed)
    assertTrue(candidateExistedBeforeRename)
    assertFalse(File(targetDir, "nexus_directory.enc.tmp").exists())
    assertEquals(1, File(targetDir, "photos").listFiles()?.size)

    // Reopen from disk and verify original profiles, photos, groups, and owner card are intact
    val reopened = LocalDirectoryRepository(filesDir = targetDir, crypto = passthroughCrypto)
    assertEquals(1, reopened.profiles.value.size)
    assertEquals("Local Original", reopened.profiles.value.single().name)
    assertArrayEquals(livePhotoBytes, reopened.loadPhotoBytes(liveProfile.photoFileName!!))
    assertEquals(listOf("Core Group"), reopened.groups.value.map { it.name })
    assertEquals("Ayush Kumar", reopened.ownerCard.value.name)

    // Retry merge and full replacement after restoring default rename behavior
    val mergeOutcome = reopened.applyMergeRestore(preview)
    assertEquals(2, mergeOutcome.mergedProfiles.size)
    reopened.applyBackup(preview)
    assertEquals(1, reopened.profiles.value.size)
    assertEquals("Incoming Rename Test", reopened.profiles.value.single().name)
  }

  @Test
  fun NX101_03_scheduledBackupStagingFailureAndSevenLatestRetention() = runTest {
    val repo = newRepo()
    repo.saveProfile(PersonProfile(id = "1", name = "Aditi Test", createdAtEpochMs = 100L, updatedAtEpochMs = 100L))

    val destDir = tempFolder.newFolder("nx101_03_dest")
    val manualFile = File(destDir, "manual-backup.nxbk").apply { writeText("manual") }
    val unrelatedFile = File(destDir, "notes.txt").apply { writeText("unrelated") }

    val destination = object : BackupDestination {
      var failWrite = false
      var corruptVerification = false
      var failRename = false
      var sawFinalFileDuringWrite = false

      override fun isAccessible(): Boolean = true
      override fun listScheduledFiles(): List<ScheduledBackupFile> =
        (destDir.listFiles() ?: emptyArray())
          .filter { it.isFile && AutoBackupRunner.isScheduledBackupFileName(it.name) }
          .map { ScheduledBackupFile(it.name) }

      override fun writeStagingAndReadBack(stagingName: String, bytes: ByteArray): ByteArray {
        assertFalse(AutoBackupRunner.isScheduledBackupFileName(stagingName))
        val target = File(destDir, stagingName)
        if (failWrite) {
          target.writeBytes(byteArrayOf(1, 2, 3))
          if (listScheduledFiles().size > 7) sawFinalFileDuringWrite = true
          throw IOException("Simulated destination write failure")
        }
        target.writeBytes(bytes)
        return if (corruptVerification) byteArrayOf(9, 8, 7) else target.readBytes()
      }

      override fun promoteStagingToFinal(stagingName: String, finalName: String): Boolean {
        if (failRename) return false
        val staged = File(destDir, stagingName)
        return staged.exists() && staged.renameTo(File(destDir, finalName))
      }

      override fun deleteFile(fileName: String): Boolean = File(destDir, fileName).delete()
    }

    val runner = AutoBackupRunner()
    var lastCreated = ""
    for (i in 1..8) {
      val res = runner.execute(
        repository = repo,
        destination = destination,
        password = "AutoPass123",
        retentionCount = 7,
        nowEpochMs = 1_700_000_000_000L + i * 1000L,
      )
      assertTrue(res.isSuccess)
      lastCreated = res.getOrThrow()
    }

    assertEquals(7, destination.listScheduledFiles().size)
    assertTrue(destination.listScheduledFiles().any { it.name == lastCreated })
    assertTrue(manualFile.exists())
    assertTrue(unrelatedFile.exists())

    // 1. Failure during write: staging cleaned up, no final .nxbk added, 7 prior backups remain
    destination.failWrite = true
    assertTrue(
      runner.execute(repo, destination, "AutoPass123", 7, 1_700_000_099_000L).isFailure,
    )
    assertFalse(destination.sawFinalFileDuringWrite)
    assertEquals(7, destination.listScheduledFiles().size)
    assertTrue(destDir.listFiles()?.none { it.name.startsWith(AutoBackupRunner.STAGING_FILE_PREFIX) } == true)
    destination.failWrite = false

    // 2. Failure during destination verification: staging cleaned up, no final .nxbk added
    destination.corruptVerification = true
    assertTrue(
      runner.execute(repo, destination, "AutoPass123", 7, 1_700_000_100_000L).isFailure,
    )
    assertEquals(7, destination.listScheduledFiles().size)
    assertTrue(destDir.listFiles()?.none { it.name.startsWith(AutoBackupRunner.STAGING_FILE_PREFIX) } == true)
    destination.corruptVerification = false

    // 3. Failure during rename/promotion: staging cleaned up, no fallback write, 7 prior backups remain
    destination.failRename = true
    assertTrue(
      runner.execute(repo, destination, "AutoPass123", 7, 1_700_000_101_000L).isFailure,
    )
    assertEquals(7, destination.listScheduledFiles().size)
    assertTrue(destDir.listFiles()?.none { it.name.startsWith(AutoBackupRunner.STAGING_FILE_PREFIX) } == true)
    assertTrue(manualFile.exists())
    assertTrue(unrelatedFile.exists())
  }

  @Test
  fun NX101_04_peopleSearchGroupTextFormattedPhonesNewFieldsAndFilters() {
    val profiles = listOf(
      PersonProfile(
        id = "1",
        name = "Aditi Sharma",
        phone = "+91 90000 01001",
        email = "aditi.sharma@gov.in",
        designation = "Joint Secretary",
        trainingGroup = "Group 1",
        postings = listOf(
          PostingEntry(
            id = "post-1",
            organisation = "Ministry of Finance",
            designation = "Joint Secretary",
            location = "New Delhi",
            isCurrent = true,
          ),
        ),
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
      PersonProfile(
        id = "2",
        name = "Kabir Verma",
        phone = "+91 98111 11111",
        email = "kabir@example.org",
        designation = "Director",
        trainingGroup = "Group 2",
        postings = listOf(
          PostingEntry(
            id = "post-2",
            organisation = "LBSNAA",
            designation = "Director",
            location = "Mussoorie",
            isCurrent = true,
          ),
        ),
        createdAtEpochMs = 200L,
        updatedAtEpochMs = 200L,
      ),
    )

    // "Group 1" must match Group 1 without matching Kabir's phone containing '1'
    val group1Matches = DirectorySearch.filterAndSort(profiles, "Group 1", null, null, false, false)
    assertEquals(listOf("1"), group1Matches.map { it.id })

    // Formatted phone search still works
    val phoneMatches = DirectorySearch.filterAndSort(profiles, "+91 90000-01001", null, null, false, false)
    assertEquals(listOf("1"), phoneMatches.map { it.id })

    // New fields: email, designation, current posting location
    assertEquals(listOf("1"), DirectorySearch.filterAndSort(profiles, "aditi.sharma@gov.in", null, null, false, false).map { it.id })
    assertEquals(listOf("1"), DirectorySearch.filterAndSort(profiles, "Joint Secretary", null, null, false, false).map { it.id })
    assertEquals(listOf("2"), DirectorySearch.filterAndSort(profiles, "Mussoorie", null, null, false, false).map { it.id })

    // Combined search + training-group filter
    val combined = DirectorySearch.filterAndSort(profiles, "Director", null, "Group 2", false, false)
    assertEquals(listOf("2"), combined.map { it.id })
    val emptyCombined = DirectorySearch.filterAndSort(profiles, "Director", null, "Group 1", false, false)
    assertTrue(emptyCombined.isEmpty())
  }

  @Test
  fun NX101_05_personEditDraftRecoverySessionIsolationAndTempPhotoCleanup() {
    val filesDir = tempFolder.newFolder("nx101_05")
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // 1. Add session with unsaved text, category, tags, expanded details, and protected temp photo
    val addInitial = PersonEditDraft.initialFor(profileId = null)
    val tempPhotoPath = DraftPhotoStorage.writeTempPhoto(filesDir, addInitial.sessionId, byteArrayOf(7, 8, 9))
    val editedAdd = addInitial.copy(
      name = "Unsaved Draft",
      category = PersonCategory.FELLOW_TRAINEE,
      tags = listOf("Trek", "Policy"),
      moreDetailsExpanded = true,
      email = "draft@example.com",
      pendingPhotoTempPath = tempPhotoPath,
    )

    // Simulate screen recreation via serialized draft state (no raw bytes in draft)
    val encodedAdd = json.encodeToString(editedAdd)
    val restoredAdd = json.decodeFromString<PersonEditDraft>(encodedAdd)
    assertEquals("Unsaved Draft", restoredAdd.name)
    assertEquals(PersonCategory.FELLOW_TRAINEE, restoredAdd.category)
    assertEquals(listOf("Trek", "Policy"), restoredAdd.tags)
    assertTrue(restoredAdd.moreDetailsExpanded)
    assertEquals(tempPhotoPath, restoredAdd.pendingPhotoTempPath)
    assertArrayEquals(byteArrayOf(7, 8, 9), DraftPhotoStorage.readTempPhoto(restoredAdd.pendingPhotoTempPath))

    // Explicit cancellation cleans up protected temp photo
    DraftPhotoStorage.deleteTempPhoto(restoredAdd.pendingPhotoTempPath)
    assertNull(DraftPhotoStorage.readTempPhoto(restoredAdd.pendingPhotoTempPath))

    // 2. Edit session initializes once and does not overwrite unsaved edits on re-init
    val savedProfile = PersonProfile(
      id = "p-edit-1",
      name = "Original Name",
      phone = "12345",
      createdAtEpochMs = 100L,
      updatedAtEpochMs = 100L,
    )
    val editDraft = PersonEditDraft.initialFor("p-edit-1").applySavedProfile(savedProfile)
    assertTrue(editDraft.isInitialized)
    val userEdited = editDraft.copy(name = "Edited Name")
    val afterRecreateInit = userEdited.applySavedProfile(savedProfile)
    assertEquals("Edited Name", afterRecreateInit.name)

    // 3. Different profile opens its own independent session
    val otherProfile = PersonProfile(
      id = "p-edit-2",
      name = "Second Person",
      createdAtEpochMs = 200L,
      updatedAtEpochMs = 200L,
    )
    val otherDraft = PersonEditDraft.initialFor("p-edit-2").applySavedProfile(otherProfile)
    assertEquals("Second Person", otherDraft.name)
    assertNotNull(otherDraft.sessionId)
    assertFalse(otherDraft.sessionId == userEdited.sessionId)
  }
}
