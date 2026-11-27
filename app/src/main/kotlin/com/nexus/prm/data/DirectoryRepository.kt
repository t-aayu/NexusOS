package com.nexus.prm.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DirectorySnapshot(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val profiles: List<PersonProfile> = emptyList(),
  val groups: List<ContactGroup> = emptyList(),
  val ownerCard: OwnerCard = OwnerCard(),
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 3
  }
}

@Serializable
data class BackupManifest(
  val schemaVersion: Int = DirectorySnapshot.CURRENT_SCHEMA_VERSION,
  val createdAtEpochMs: Long,
  val profiles: List<PersonProfile>,
  val groups: List<ContactGroup> = emptyList(),
  val ownerCard: OwnerCard = OwnerCard(),
)

data class BackupPreview(
  val profiles: List<PersonProfile>,
  val groups: List<ContactGroup>,
  val ownerCard: OwnerCard,
  val photos: Map<String, ByteArray>,
  val createdAtEpochMs: Long,
)

interface DirectoryRepository {
  val profiles: StateFlow<List<PersonProfile>>
  val groups: StateFlow<List<ContactGroup>>
  val ownerCard: StateFlow<OwnerCard>

  suspend fun getProfile(id: String): PersonProfile?
  suspend fun saveProfile(
    profile: PersonProfile,
    pendingPhotoBytes: ByteArray? = null,
    removePhoto: Boolean = false,
  ): PersonProfile

  suspend fun saveProfilesBatch(entries: List<PersonProfile>): List<PersonProfile>
  suspend fun deleteProfile(id: String)
  suspend fun toggleFavourite(id: String)

  suspend fun addOrUpdatePosting(profileId: String, entry: PostingEntry)
  suspend fun setCurrentPosting(profileId: String, postingId: String)
  suspend fun addIntroduction(profileId: String, intro: IntroductionRecord)

  suspend fun createGroup(name: String): ContactGroup
  suspend fun renameGroup(groupId: String, newName: String)
  suspend fun deleteGroup(groupId: String)
  suspend fun setGroupMembers(groupId: String, memberIds: List<String>)

  suspend fun saveOwnerCard(card: OwnerCard)

  suspend fun loadPhotoBytes(photoFileName: String): ByteArray?
  suspend fun exportBackup(password: String): ByteArray
  suspend fun inspectBackup(encryptedBytes: ByteArray, password: String): BackupPreview
  suspend fun applyBackup(preview: BackupPreview)
  suspend fun applyMergeRestore(preview: BackupPreview, keepDuplicateIds: Set<String> = emptySet()): MergeOutcome
}

class LocalDirectoryRepository(
  private val filesDir: File,
  private val crypto: LocalStorageCrypto,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DirectoryRepository {

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }
  private val mutex = Mutex()
  private val directoryFile = File(filesDir, "nexus_directory.enc")
  private val photosDir = File(filesDir, "photos")

  private val initialSnapshot = loadInitialSnapshot()
  private val _profiles = MutableStateFlow(initialSnapshot.profiles.map { it.withSyncedPostings() })
  override val profiles: StateFlow<List<PersonProfile>> = _profiles.asStateFlow()

  private val _groups = MutableStateFlow(initialSnapshot.groups)
  override val groups: StateFlow<List<ContactGroup>> = _groups.asStateFlow()

  private val _ownerCard = MutableStateFlow(initialSnapshot.ownerCard)
  override val ownerCard: StateFlow<OwnerCard> = _ownerCard.asStateFlow()

  private fun loadInitialSnapshot(): DirectorySnapshot {
    if (!directoryFile.exists()) return DirectorySnapshot()
    val encrypted = directoryFile.readBytes()
    val decrypted = crypto.decrypt(encrypted)
    return json.decodeFromString<DirectorySnapshot>(decrypted.decodeToString())
  }

  override suspend fun getProfile(id: String): PersonProfile? =
    _profiles.value.firstOrNull { it.id == id }

  override suspend fun saveProfile(
    profile: PersonProfile,
    pendingPhotoBytes: ByteArray?,
    removePhoto: Boolean,
  ): PersonProfile = withContext(ioDispatcher) {
    mutex.withLock {
      val trimmedName = profile.name.trim()
      require(trimmedName.isNotEmpty()) { "Name must not be blank" }

      val existing = _profiles.value.firstOrNull { it.id == profile.id }
      val oldPhoto = existing?.photoFileName

      val newPhotoFileName = when {
        pendingPhotoBytes != null -> {
          val fileName = "${UUID.randomUUID()}.jpg"
          writeEncryptedPhoto(fileName, pendingPhotoBytes)
          fileName
        }
        removePhoto -> null
        else -> profile.photoFileName
      }

      val now = System.currentTimeMillis()
      val normalized = normalizeProfile(
        profile = profile.copy(
          photoFileName = newPhotoFileName,
          postings = existing?.postings ?: profile.postings,
          introductions = existing?.introductions ?: profile.introductions,
          createdAtEpochMs = existing?.createdAtEpochMs ?: profile.createdAtEpochMs,
          updatedAtEpochMs = now,
        ),
        existingProfiles = _profiles.value.filterNot { it.id == profile.id },
      ).let { saved ->
        // If user edited organisation/designation directly and there is a current posting, keep current posting synced
        val cur = saved.currentPosting()
        if (cur != null && (cur.organisation != profile.organisation.trim() || cur.designation != profile.designation.trim())) {
          val updatedPostings = saved.postings.map {
            if (it.id == cur.id) it.copy(organisation = profile.organisation.trim(), designation = profile.designation.trim()) else it
          }
          saved.copy(postings = updatedPostings).withSyncedPostings()
        } else {
          saved.withSyncedPostings()
        }
      }

      val updatedList = if (existing == null) {
        _profiles.value + normalized
      } else {
        _profiles.value.map { if (it.id == normalized.id) normalized else it }
      }

      persistSnapshot(updatedList, _groups.value, _ownerCard.value)
      _profiles.value = updatedList

      if (oldPhoto != null && oldPhoto != newPhotoFileName) {
        deletePhotoFile(oldPhoto)
      }

      normalized
    }
  }

  override suspend fun saveProfilesBatch(entries: List<PersonProfile>): List<PersonProfile> =
    withContext(ioDispatcher) {
      mutex.withLock {
        if (entries.isEmpty()) return@withLock emptyList()
        val now = System.currentTimeMillis()
        val running = _profiles.value.toMutableList()
        val saved = mutableListOf<PersonProfile>()

        for ((index, entry) in entries.withIndex()) {
          val normalized = normalizeProfile(
            profile = entry.copy(
              id = UUID.randomUUID().toString(),
              createdAtEpochMs = now + index,
              updatedAtEpochMs = now + index,
            ),
            existingProfiles = running,
          ).withSyncedPostings()
          running += normalized
          saved += normalized
        }

        persistSnapshot(running, _groups.value, _ownerCard.value)
        _profiles.value = running
        saved
      }
    }

  override suspend fun addOrUpdatePosting(profileId: String, entry: PostingEntry): Unit =
    withContext(ioDispatcher) {
      mutex.withLock {
        require(PostingValidation.isValidDateRange(entry.startDate, entry.endDate)) {
          "End date cannot be earlier than start date"
        }
        val target = _profiles.value.firstOrNull { it.id == profileId } ?: return@withLock
        val cleaned = entry.copy(
          organisation = entry.organisation.trim(),
          designation = entry.designation.trim(),
          location = entry.location.trim(),
          startDate = entry.startDate.trim(),
          endDate = entry.endDate.trim(),
        )
        val existingIdx = target.postings.indexOfFirst { it.id == cleaned.id }
        val baseList = if (existingIdx >= 0) {
          target.postings.map { if (it.id == cleaned.id) cleaned else it }
        } else {
          target.postings + cleaned
        }
        val updatedPostings = if (cleaned.isCurrent) {
          baseList.map { it.copy(isCurrent = it.id == cleaned.id) }
        } else {
          baseList
        }
        val updatedProfile = target.copy(
          postings = updatedPostings,
          updatedAtEpochMs = System.currentTimeMillis(),
        ).withSyncedPostings()
        val updatedList = _profiles.value.map { if (it.id == profileId) updatedProfile else it }
        persistSnapshot(updatedList, _groups.value, _ownerCard.value)
        _profiles.value = updatedList
      }
    }

  override suspend fun setCurrentPosting(profileId: String, postingId: String): Unit =
    withContext(ioDispatcher) {
      mutex.withLock {
        val target = _profiles.value.firstOrNull { it.id == profileId } ?: return@withLock
        val updatedPostings = target.postings.map { it.copy(isCurrent = it.id == postingId) }
        val updatedProfile = target.copy(
          postings = updatedPostings,
          updatedAtEpochMs = System.currentTimeMillis(),
        ).withSyncedPostings()
        val updatedList = _profiles.value.map { if (it.id == profileId) updatedProfile else it }
        persistSnapshot(updatedList, _groups.value, _ownerCard.value)
        _profiles.value = updatedList
      }
    }

  override suspend fun addIntroduction(profileId: String, intro: IntroductionRecord): Unit =
    withContext(ioDispatcher) {
      mutex.withLock {
        val trimmedName = intro.introducerName.trim()
        require(trimmedName.isNotEmpty()) { "Introducer name must not be blank" }
        val target = _profiles.value.firstOrNull { it.id == profileId } ?: return@withLock
        val cleaned = intro.copy(
          introducerName = trimmedName,
          date = intro.date.trim(),
          contextNote = intro.contextNote.trim(),
        )
        val updatedProfile = target.copy(
          introductions = target.introductions + cleaned,
          updatedAtEpochMs = System.currentTimeMillis(),
        )
        val updatedList = _profiles.value.map { if (it.id == profileId) updatedProfile else it }
        persistSnapshot(updatedList, _groups.value, _ownerCard.value)
        _profiles.value = updatedList
      }
    }

  private fun normalizeProfile(
    profile: PersonProfile,
    existingProfiles: List<PersonProfile>,
  ): PersonProfile {
    val trimmedName = profile.name.trim()
    require(trimmedName.isNotEmpty()) { "Name must not be blank" }
    return profile.copy(
      name = trimmedName,
      phone = profile.phone.trim(),
      whereWeMet = profile.whereWeMet.trim(),
      email = profile.email.trim(),
      organisation = profile.organisation.trim(),
      designation = profile.designation.trim(),
      service = profile.service.trim(),
      cadre = profile.cadre.trim(),
      serviceBatch = profile.serviceBatch.trim(),
      foundationCourseBatch = profile.foundationCourseBatch.trim(),
      trainingGroup = profile.trainingGroup.trim(),
      tags = DirectorySearch.normalizeTags(profile.tags, existingProfiles),
    )
  }

  override suspend fun deleteProfile(id: String): Unit = withContext(ioDispatcher) {
    mutex.withLock {
      val target = _profiles.value.firstOrNull { it.id == id } ?: return@withLock
      val updatedList = _profiles.value.filterNot { it.id == id }
      val updatedGroups = _groups.value.map { group ->
        if (id in group.memberIds) group.copy(memberIds = group.memberIds.filterNot { it == id }) else group
      }
      persistSnapshot(updatedList, updatedGroups, _ownerCard.value)
      _profiles.value = updatedList
      _groups.value = updatedGroups
      target.photoFileName?.let(::deletePhotoFile)
    }
  }

  override suspend fun toggleFavourite(id: String): Unit = withContext(ioDispatcher) {
    mutex.withLock {
      val target = _profiles.value.firstOrNull { it.id == id } ?: return@withLock
      val updated = target.copy(
        isFavourite = !target.isFavourite,
        updatedAtEpochMs = System.currentTimeMillis(),
      )
      val updatedList = _profiles.value.map { if (it.id == id) updated else it }
      persistSnapshot(updatedList, _groups.value, _ownerCard.value)
      _profiles.value = updatedList
    }
  }

  override suspend fun createGroup(name: String): ContactGroup = withContext(ioDispatcher) {
    mutex.withLock {
      val trimmed = name.trim()
      require(trimmed.isNotEmpty()) { "Group name must not be blank" }
      require(_groups.value.none { it.name.equals(trimmed, ignoreCase = true) }) {
        "A group named '$trimmed' already exists"
      }
      val group = ContactGroup(
        id = UUID.randomUUID().toString(),
        name = trimmed,
        memberIds = emptyList(),
        createdAtEpochMs = System.currentTimeMillis(),
      )
      val updatedGroups = _groups.value + group
      persistSnapshot(_profiles.value, updatedGroups, _ownerCard.value)
      _groups.value = updatedGroups
      group
    }
  }

  override suspend fun renameGroup(groupId: String, newName: String): Unit = withContext(ioDispatcher) {
    mutex.withLock {
      val trimmed = newName.trim()
      require(trimmed.isNotEmpty()) { "Group name must not be blank" }
      require(_groups.value.none { it.id != groupId && it.name.equals(trimmed, ignoreCase = true) }) {
        "A group named '$trimmed' already exists"
      }
      val updatedGroups = _groups.value.map {
        if (it.id == groupId) it.copy(name = trimmed) else it
      }
      persistSnapshot(_profiles.value, updatedGroups, _ownerCard.value)
      _groups.value = updatedGroups
    }
  }

  override suspend fun deleteGroup(groupId: String): Unit = withContext(ioDispatcher) {
    mutex.withLock {
      val updatedGroups = _groups.value.filterNot { it.id == groupId }
      persistSnapshot(_profiles.value, updatedGroups, _ownerCard.value)
      _groups.value = updatedGroups
    }
  }

  override suspend fun setGroupMembers(groupId: String, memberIds: List<String>): Unit =
    withContext(ioDispatcher) {
      mutex.withLock {
        val validIds = _profiles.value.map { it.id }.toSet()
        val cleanedMembers = memberIds.filter { it in validIds }.distinct()
        val updatedGroups = _groups.value.map {
          if (it.id == groupId) it.copy(memberIds = cleanedMembers) else it
        }
        persistSnapshot(_profiles.value, updatedGroups, _ownerCard.value)
        _groups.value = updatedGroups
      }
    }

  override suspend fun saveOwnerCard(card: OwnerCard): Unit = withContext(ioDispatcher) {
    mutex.withLock {
      val cleaned = card.copy(
        name = card.name.trim(),
        phone = card.phone.trim(),
        email = card.email.trim(),
        organisation = card.organisation.trim(),
        designation = card.designation.trim(),
        service = card.service.trim(),
        cadre = card.cadre.trim(),
        serviceBatch = card.serviceBatch.trim(),
        foundationCourseBatch = card.foundationCourseBatch.trim(),
        trainingGroup = card.trainingGroup.trim(),
      )
      persistSnapshot(_profiles.value, _groups.value, cleaned)
      _ownerCard.value = cleaned
    }
  }

  override suspend fun loadPhotoBytes(photoFileName: String): ByteArray? = withContext(ioDispatcher) {
    val file = safePhotoFile(photoFileName) ?: return@withContext null
    if (!file.exists()) return@withContext null
    runCatching { crypto.decrypt(file.readBytes()) }.getOrNull()
  }

  override suspend fun exportBackup(password: String): ByteArray = withContext(ioDispatcher) {
    val (currentProfiles, currentGroups, currentOwnerCard) = mutex.withLock {
      Triple(_profiles.value, _groups.value, _ownerCard.value)
    }
    val manifest = BackupManifest(
      schemaVersion = DirectorySnapshot.CURRENT_SCHEMA_VERSION,
      createdAtEpochMs = System.currentTimeMillis(),
      profiles = currentProfiles,
      groups = currentGroups,
      ownerCard = currentOwnerCard,
    )
    val zipBytes = ByteArrayOutputStream().use { baos ->
      ZipOutputStream(baos).use { zip ->
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(json.encodeToString(manifest).encodeToByteArray())
        zip.closeEntry()

        for (profile in currentProfiles) {
          val photoName = profile.photoFileName ?: continue
          val photoBytes = loadPhotoBytes(photoName) ?: continue
          zip.putNextEntry(ZipEntry("$PHOTOS_PREFIX$photoName"))
          zip.write(photoBytes)
          zip.closeEntry()
        }
      }
      baos.toByteArray()
    }
    BackupCrypto.encrypt(zipBytes, password)
  }

  override suspend fun inspectBackup(encryptedBytes: ByteArray, password: String): BackupPreview =
    withContext(ioDispatcher) {
      val zipBytes = BackupCrypto.decrypt(encryptedBytes, password)
      var manifestJson: String? = null
      val photos = mutableMapOf<String, ByteArray>()

      ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
          if (!entry.isDirectory) {
            when {
              entry.name == MANIFEST_ENTRY -> {
                manifestJson = zip.readBytes().decodeToString()
              }
              entry.name.startsWith(PHOTOS_PREFIX) -> {
                val photoName = entry.name.removePrefix(PHOTOS_PREFIX)
                require(!photoName.contains('/') && !photoName.contains('\\') && photoName.isNotBlank()) {
                  "Invalid photo entry in backup"
                }
                photos[photoName] = zip.readBytes()
              }
            }
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }

      val rawManifest = manifestJson ?: throw IllegalArgumentException("Backup is missing manifest")
      val manifest = json.decodeFromString<BackupManifest>(rawManifest)
      require(manifest.schemaVersion in 1..DirectorySnapshot.CURRENT_SCHEMA_VERSION) {
        "Unsupported backup schema version: ${manifest.schemaVersion}"
      }
      for (profile in manifest.profiles) {
        require(profile.name.isNotBlank()) { "Backup contains an invalid profile with blank name" }
        val photoName = profile.photoFileName
        if (photoName != null) {
          val bytes = photos[photoName]
          require(bytes != null && bytes.isNotEmpty()) {
            "Backup is missing photo data for ${profile.name}"
          }
        }
      }

      val validIds = manifest.profiles.map { it.id }.toSet()
      val sanitizedGroups = manifest.groups.map { group ->
        group.copy(memberIds = group.memberIds.filter { it in validIds }.distinct())
      }

      BackupPreview(
        profiles = manifest.profiles.map { it.withSyncedPostings() },
        groups = sanitizedGroups,
        ownerCard = manifest.ownerCard,
        photos = photos,
        createdAtEpochMs = manifest.createdAtEpochMs,
      )
    }

  override suspend fun applyBackup(preview: BackupPreview): Unit = withContext(ioDispatcher) {
    mutex.withLock {
      val oldPhotos = _profiles.value.mapNotNull { it.photoFileName }.toSet()
      val newPhotos = mutableSetOf<String>()
      val writtenNow = mutableListOf<String>()
      val syncedProfiles = preview.profiles.map { it.withSyncedPostings() }

      try {
        for (profile in syncedProfiles) {
          val photoName = profile.photoFileName ?: continue
          val bytes = preview.photos[photoName]
            ?: throw IllegalStateException("Missing photo bytes for $photoName")
          writeEncryptedPhoto(photoName, bytes)
          newPhotos += photoName
          if (photoName !in oldPhotos) writtenNow += photoName
        }

        persistSnapshot(syncedProfiles, preview.groups, preview.ownerCard)
        _profiles.value = syncedProfiles
        _groups.value = preview.groups
        _ownerCard.value = preview.ownerCard

        for (orphan in oldPhotos - newPhotos) {
          deletePhotoFile(orphan)
        }
      } catch (t: Throwable) {
        for (staged in writtenNow) {
          deletePhotoFile(staged)
        }
        throw t
      }
    }
  }

  override suspend fun applyMergeRestore(
    preview: BackupPreview,
    keepDuplicateIds: Set<String>,
  ): MergeOutcome = withContext(ioDispatcher) {
    mutex.withLock {
      val existingPhotos = _profiles.value.mapNotNull { it.photoFileName }.toSet()
      val outcome = RestoreMerger.computeMergeOutcome(
        localProfiles = _profiles.value,
        localGroups = _groups.value,
        localOwnerCard = _ownerCard.value,
        backupPreview = preview,
        keepDuplicateIds = keepDuplicateIds,
      )

      val writtenNow = mutableListOf<String>()
      try {
        for ((photoName, bytes) in outcome.photosToWrite) {
          writeEncryptedPhoto(photoName, bytes)
          if (photoName !in existingPhotos) writtenNow += photoName
        }

        persistSnapshot(outcome.mergedProfiles, outcome.mergedGroups, outcome.mergedOwnerCard)
        _profiles.value = outcome.mergedProfiles
        _groups.value = outcome.mergedGroups
        _ownerCard.value = outcome.mergedOwnerCard
        outcome
      } catch (t: Throwable) {
        for (staged in writtenNow) {
          deletePhotoFile(staged)
        }
        throw t
      }
    }
  }

  private fun persistSnapshot(
    profiles: List<PersonProfile>,
    groups: List<ContactGroup>,
    ownerCard: OwnerCard,
  ) {
    if (!filesDir.exists()) filesDir.mkdirs()
    val snapshot = DirectorySnapshot(
      schemaVersion = DirectorySnapshot.CURRENT_SCHEMA_VERSION,
      profiles = profiles,
      groups = groups,
      ownerCard = ownerCard,
    )
    val encrypted = crypto.encrypt(json.encodeToString(snapshot).encodeToByteArray())
    val tmpFile = File(filesDir, "nexus_directory.enc.tmp")
    tmpFile.writeBytes(encrypted)
    if (!tmpFile.renameTo(directoryFile)) {
      directoryFile.writeBytes(encrypted)
      tmpFile.delete()
    }
  }

  private fun writeEncryptedPhoto(photoFileName: String, bytes: ByteArray) {
    if (!photosDir.exists()) photosDir.mkdirs()
    val target = safePhotoFile(photoFileName)
      ?: throw IllegalArgumentException("Invalid photo file name")
    val tmp = File(photosDir, "$photoFileName.tmp")
    tmp.writeBytes(crypto.encrypt(bytes))
    if (!tmp.renameTo(target)) {
      target.writeBytes(crypto.encrypt(bytes))
      tmp.delete()
    }
  }

  private fun deletePhotoFile(photoFileName: String) {
    safePhotoFile(photoFileName)?.delete()
  }

  private fun safePhotoFile(photoFileName: String): File? {
    if (photoFileName.isBlank() || photoFileName.contains('/') || photoFileName.contains('\\')) {
      return null
    }
    return File(photosDir, photoFileName)
  }

  companion object {
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val PHOTOS_PREFIX = "photos/"
  }
}
