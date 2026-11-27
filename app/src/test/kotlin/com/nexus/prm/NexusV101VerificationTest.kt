package com.nexus.prm

import com.nexus.prm.data.AutoBackupRunner
import com.nexus.prm.data.BackupCrypto
import com.nexus.prm.data.BackupDestination
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.FixedBackupLocation
import com.nexus.prm.data.LocalDirectoryRepository
import com.nexus.prm.data.LocalStorageCrypto
import com.nexus.prm.data.OwnerCard
import com.nexus.prm.data.OwnerCardVCard
import com.nexus.prm.data.PersonCategory
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.PostingEntry
import com.nexus.prm.data.ScheduledBackupFile
import com.nexus.prm.ui.people.personCardSubtitle
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

  @Test
  fun fixedBackupLocation_classifiesNexusOsBackupParentAndOtherFolders() {
    assertEquals(
      FixedBackupLocation.SelectionKind.BACKUP_FOLDER,
      FixedBackupLocation.classifyDocumentId("primary:NexusOS/Backup"),
    )
    assertEquals(
      FixedBackupLocation.SelectionKind.BACKUP_FOLDER,
      FixedBackupLocation.classifyDocumentId("primary:nexusos/backup/"),
    )
    assertEquals(
      FixedBackupLocation.SelectionKind.PARENT_FOLDER,
      FixedBackupLocation.classifyDocumentId("primary:NexusOS"),
    )
    assertEquals(
      FixedBackupLocation.SelectionKind.OTHER,
      FixedBackupLocation.classifyDocumentId("primary:Download"),
    )
    assertEquals(
      FixedBackupLocation.SelectionKind.OTHER,
      FixedBackupLocation.classifyDocumentId("1234-5678:NexusOS/Backup"),
    )
    assertFalse(AutoBackupRunner.isScheduledBackupFileName("manual-backup.nxbk"))
    assertTrue(AutoBackupRunner.isScheduledBackupFileName("nexus-auto-20261127-140000-000.nxbk"))
  }

  @Test
  fun customCategories_createCaseInsensitiveReuseFilterRestartAndBackupRestore() = runTest {
    val repoDir = tempFolder.newFolder("custom_cat_repo")
    val repo = newRepo(repoDir)

    val created = repo.addCustomCategory("Heritage Walk Group")
    assertEquals("Heritage Walk Group", created)

    // Trimming and case-insensitive reuse
    val reused = repo.addCustomCategory("  heritage walk group  ")
    assertEquals("Heritage Walk Group", reused)
    assertEquals(listOf("Heritage Walk Group"), repo.customCategories.value)

    // Built-in category label reuse
    assertEquals("Faculty", repo.addCustomCategory(" faculty "))
    assertEquals(listOf("Heritage Walk Group"), repo.customCategories.value)

    val p1 = repo.saveProfile(
      PersonProfile(
        id = "c1",
        name = "Walker One",
        customCategory = "Heritage Walk Group",
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
    )
    val p2 = repo.saveProfile(
      PersonProfile(
        id = "c2",
        name = "Walker Two",
        customCategory = "  heritage walk group ",
        createdAtEpochMs = 200L,
        updatedAtEpochMs = 200L,
      ),
    )
    repo.saveProfile(
      PersonProfile(
        id = "c3",
        name = "Faculty Member",
        category = PersonCategory.FACULTY,
        createdAtEpochMs = 300L,
        updatedAtEpochMs = 300L,
      ),
    )
    assertEquals("Heritage Walk Group", p1.categoryLabel())
    assertEquals("Heritage Walk Group", p2.categoryLabel())

    // Filter People by custom category
    val filtered = DirectorySearch.filterAndSort(
      profiles = repo.profiles.value,
      query = "",
      selectedCategory = null,
      selectedTrainingGroup = null,
      favouritesOnly = false,
      recentlyAddedOnly = false,
      selectedCategoryLabel = "Heritage Walk Group",
    )
    assertEquals(listOf("c1", "c2"), filtered.map { it.id })

    // Reopen from disk: custom categories survive restart
    val reopened = newRepo(repoDir)
    assertEquals(listOf("Heritage Walk Group"), reopened.customCategories.value)

    // Backup & restore (full replacement and merge)
    val backupBytes = reopened.exportBackup("CatPass123!")
    val cleanTarget = newRepo()
    val preview = cleanTarget.inspectBackup(backupBytes, "CatPass123!")
    assertEquals(listOf("Heritage Walk Group"), preview.customCategories)
    cleanTarget.applyBackup(preview)
    assertEquals(listOf("Heritage Walk Group"), cleanTarget.customCategories.value)
    assertEquals(
      2,
      DirectorySearch.filterAndSort(
        cleanTarget.profiles.value,
        "",
        null,
        null,
        false,
        false,
        selectedCategoryLabel = "Heritage Walk Group",
      ).size,
    )

    val mergeTarget = newRepo()
    mergeTarget.addCustomCategory("Local Only Category")
    mergeTarget.applyMergeRestore(mergeTarget.inspectBackup(backupBytes, "CatPass123!"))
    assertEquals(listOf("Local Only Category", "Heritage Walk Group"), mergeTarget.customCategories.value)
  }

  @Test
  fun otherDetails_persistsMultilineTextSurvivesDraftAndMergesWithOlderBackups() = runTest {
    val repo = newRepo()
    val multilineOther = "Line 1: Speaks fluent Tamil\nLine 2: Prefers morning calls"
    val saved = repo.saveProfile(
      PersonProfile(
        id = "od-1",
        name = "Meera Notes",
        whereWeMet = "LBSNAA Mess",
        otherDetails = multilineOther,
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
      ),
    )
    assertEquals("LBSNAA Mess", saved.whereWeMet)
    assertEquals(multilineOther, saved.otherDetails)

    // Draft round-trip preserves otherDetails
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val draft = PersonEditDraft.initialFor(saved.id).applySavedProfile(saved).copy(
      otherDetails = "Updated line 1\nUpdated line 2",
    )
    val restoredDraft = json.decodeFromString<PersonEditDraft>(json.encodeToString(draft))
    assertEquals("Updated line 1\nUpdated line 2", restoredDraft.otherDetails)

    // Older backup without otherDetails defaults to empty
    val olderJson = """
      {
        "schemaVersion": 3,
        "createdAtEpochMs": 1000,
        "profiles": [
          {
            "id": "od-2",
            "name": "Older Backup Person",
            "createdAtEpochMs": 1000,
            "updatedAtEpochMs": 1000
          }
        ]
      }
    """.trimIndent()
    val olderZip = java.io.ByteArrayOutputStream().use { baos ->
      java.util.zip.ZipOutputStream(baos).use { zip ->
        zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
        zip.write(olderJson.encodeToByteArray())
        zip.closeEntry()
      }
      baos.toByteArray()
    }
    val olderBackup = BackupCrypto.encrypt(olderZip, "OldPass!")
    val olderPreview = repo.inspectBackup(olderBackup, "OldPass!")
    assertEquals("", olderPreview.profiles.single().otherDetails)

    // Merge keeps nonblank local otherDetails and fills blank local otherDetails from backup
    val backupWithOther = repo.exportBackup("NewPass!")
    val mergeTarget = newRepo()
    mergeTarget.saveProfile(
      PersonProfile(
        id = "od-1",
        name = "Meera Notes",
        otherDetails = "Keep my local note",
        createdAtEpochMs = 50L,
        updatedAtEpochMs = 50L,
      ),
    )
    mergeTarget.applyMergeRestore(mergeTarget.inspectBackup(backupWithOther, "NewPass!"))
    assertEquals("Keep my local note", mergeTarget.getProfile("od-1")!!.otherDetails)

    val emptyLocalTarget = newRepo()
    emptyLocalTarget.saveProfile(
      PersonProfile(
        id = "od-1",
        name = "Meera Notes",
        otherDetails = "",
        createdAtEpochMs = 50L,
        updatedAtEpochMs = 50L,
      ),
    )
    emptyLocalTarget.applyMergeRestore(emptyLocalTarget.inspectBackup(backupWithOther, "NewPass!"))
    assertEquals(multilineOther, emptyLocalTarget.getProfile("od-1")!!.otherDetails)
  }

  @Test
  fun personCardSubtitle_showsUpToThreeFromServiceBatchCadreThenDesignationOrg() {
    val full = PersonProfile(
      id = "1",
      name = "Aaditya",
      service = "IAS",
      serviceBatch = "2018",
      cadre = "UP",
      designation = "DM",
      organisation = "State Gov",
      createdAtEpochMs = 1L,
      updatedAtEpochMs = 1L,
    )
    assertEquals("IAS \u00B7 2018 \u00B7 UP", personCardSubtitle(full))

    val partial = full.copy(serviceBatch = "", cadre = "")
    assertEquals("IAS \u00B7 DM \u00B7 State Gov", personCardSubtitle(partial))

    val twoPrimaryOneFallback = full.copy(serviceBatch = "")
    assertEquals("IAS \u00B7 UP \u00B7 DM", personCardSubtitle(twoPrimaryOneFallback))
    assertFalse(personCardSubtitle(twoPrimaryOneFallback).contains("Â"))
  }

  @Test
  fun buildContactVCard_includesNamePhoneEmailExcludesPrivateFieldsAndSupportsNameOnly() {
    val full = PersonProfile(
      id = "v1",
      name = "Priya Nair",
      phone = "+91 98765 43210",
      email = "priya@example.com",
      whereWeMet = "Secret meeting spot",
      otherDetails = "Private note line",
      tags = listOf("ConfidentialTag"),
      photoFileName = "photo.jpg",
      postings = listOf(PostingEntry(id = "p1", organisation = "SecretOrg", isCurrent = true)),
      introductions = listOf(com.nexus.prm.data.IntroductionRecord(id = "i1", introducerName = "PrivateIntroducer")),
      createdAtEpochMs = 1L,
      updatedAtEpochMs = 1L,
    )
    val vcard = OwnerCardVCard.buildContactVCard(full)
    assertTrue(vcard.contains("BEGIN:VCARD"))
    assertTrue(vcard.contains("FN:Priya Nair"))
    assertTrue(vcard.contains("TEL;TYPE=CELL,HOME,PREF:+91 98765 43210"))
    assertTrue(vcard.contains("EMAIL;TYPE=INTERNET,HOME,PREF:priya@example.com"))
    assertTrue(vcard.contains("END:VCARD"))
    assertFalse(vcard.contains("Secret meeting spot"))
    assertFalse(vcard.contains("Private note line"))
    assertFalse(vcard.contains("ConfidentialTag"))
    assertFalse(vcard.contains("photo.jpg"))
    assertFalse(vcard.contains("SecretOrg"))
    assertFalse(vcard.contains("PrivateIntroducer"))

    val nameOnly = PersonProfile(
      id = "v2",
      name = "Name Only",
      createdAtEpochMs = 1L,
      updatedAtEpochMs = 1L,
    )
    val nameOnlyVCard = OwnerCardVCard.buildContactVCard(nameOnly)
    assertTrue(nameOnlyVCard.contains("FN:Name Only"))
    assertFalse(nameOnlyVCard.contains("TEL"))
    assertFalse(nameOnlyVCard.contains("EMAIL"))
  }
}
