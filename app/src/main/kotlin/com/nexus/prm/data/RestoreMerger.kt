package com.nexus.prm.data

enum class RestoreMode {
  MERGE,
  FULL_REPLACEMENT,
}

data class MergeDuplicateItem(
  val incomingProfile: PersonProfile,
  val matchedLocalProfile: PersonProfile,
  val matchedField: DuplicateMatchField,
  val keepSeparately: Boolean = false,
)

data class MergePreview(
  val toAdd: List<PersonProfile>,
  val matchedById: List<Pair<PersonProfile, PersonProfile>>,
  val duplicates: List<MergeDuplicateItem>,
  val localOnlyCount: Int,
)

data class MergeOutcome(
  val mergedProfiles: List<PersonProfile>,
  val mergedGroups: List<ContactGroup>,
  val mergedOwnerCard: OwnerCard,
  val photosToWrite: Map<String, ByteArray>,
  val photoAssignmentsByProfileId: Map<String, String> = emptyMap(),
  val mergedCustomCategories: List<String> = emptyList(),
)

object RestoreMerger {

  fun buildPreview(
    localProfiles: List<PersonProfile>,
    backupPreview: BackupPreview,
  ): MergePreview {
    val localById = localProfiles.associateBy { it.id }
    val toAdd = mutableListOf<PersonProfile>()
    val matchedById = mutableListOf<Pair<PersonProfile, PersonProfile>>()
    val duplicates = mutableListOf<MergeDuplicateItem>()
    val matchedLocalIds = mutableSetOf<String>()

    for (incoming in backupPreview.profiles) {
      val sameIdLocal = localById[incoming.id]
      if (sameIdLocal != null) {
        matchedById += sameIdLocal to incoming
        matchedLocalIds += sameIdLocal.id
        continue
      }

      val dupMatch = DuplicateMatcher.findMatch(
        phone = incoming.phone,
        email = incoming.email,
        candidates = localProfiles,
      )
      if (dupMatch != null) {
        duplicates += MergeDuplicateItem(
          incomingProfile = incoming,
          matchedLocalProfile = dupMatch.existingProfile,
          matchedField = dupMatch.matchedField,
          keepSeparately = false,
        )
      } else {
        toAdd += incoming
      }
    }

    val localOnlyCount = localProfiles.count { it.id !in matchedLocalIds }
    return MergePreview(
      toAdd = toAdd,
      matchedById = matchedById,
      duplicates = duplicates,
      localOnlyCount = localOnlyCount,
    )
  }

  fun computeMergeOutcome(
    localProfiles: List<PersonProfile>,
    localGroups: List<ContactGroup>,
    localOwnerCard: OwnerCard,
    backupPreview: BackupPreview,
    keepDuplicateIds: Set<String>,
    localCustomCategories: List<String> = emptyList(),
  ): MergeOutcome {
    val preview = buildPreview(localProfiles, backupPreview)
    val mergedById = linkedMapOf<String, PersonProfile>()
    val photosToWrite = linkedMapOf<String, ByteArray>()
    val photoAssignmentsByProfileId = linkedMapOf<String, String>()
    val includedIncomingIds = mutableSetOf<String>()

    for (local in localProfiles) {
      mergedById[local.id] = local.withSyncedPostings()
    }

    // 1. Merge profiles matched by stable ID
    for ((rawLocal, rawIncoming) in preview.matchedById) {
      val local = rawLocal.withSyncedPostings()
      val incoming = rawIncoming.withSyncedPostings()
      includedIncomingIds += incoming.id

      val mergedPhoto = when {
        local.photoFileName != null -> local.photoFileName
        incoming.photoFileName != null -> {
          val bytes = backupPreview.photos[incoming.photoFileName]
          if (bytes != null && bytes.isNotEmpty()) {
            photosToWrite[incoming.photoFileName] = bytes
            photoAssignmentsByProfileId[local.id] = incoming.photoFileName
            incoming.photoFileName
          } else {
            null
          }
        }
        else -> null
      }

      // Merge postings: keep local on ID conflict and preserve local current-role selection
      val localPostingIds = local.postings.map { it.id }.toSet()
      val localHasCurrent = local.postings.any { it.isCurrent }
      val addedPostings = incoming.postings
        .filter { it.id !in localPostingIds }
        .map { p -> if (localHasCurrent) p.copy(isCurrent = false) else p }
      val mergedPostings = local.postings + addedPostings

      // Merge introductions: keep local on ID conflict, add missing by ID
      val localIntroIds = local.introductions.map { it.id }.toSet()
      val mergedIntros = local.introductions + incoming.introductions.filter { it.id !in localIntroIds }

      val hasLocalCategory = local.categoryLabel() != null
      val merged = local.copy(
        name = local.name.ifBlank { incoming.name },
        phone = local.phone.ifBlank { incoming.phone },
        category = if (hasLocalCategory) local.category else incoming.category,
        customCategory = if (hasLocalCategory) local.customCategory else incoming.customCategory,
        whereWeMet = local.whereWeMet.ifBlank { incoming.whereWeMet },
        email = local.email.ifBlank { incoming.email },
        organisation = local.organisation.ifBlank { incoming.organisation },
        designation = local.designation.ifBlank { incoming.designation },
        service = local.service.ifBlank { incoming.service },
        cadre = local.cadre.ifBlank { incoming.cadre },
        serviceBatch = local.serviceBatch.ifBlank { incoming.serviceBatch },
        foundationCourseBatch = local.foundationCourseBatch.ifBlank { incoming.foundationCourseBatch },
        trainingGroup = local.trainingGroup.ifBlank { incoming.trainingGroup },
        otherDetails = local.otherDetails.ifBlank { incoming.otherDetails },
        isFavourite = local.isFavourite,
        tags = DirectorySearch.normalizeTags(local.tags + incoming.tags, mergedById.values.toList()),
        postings = mergedPostings,
        introductions = mergedIntros,
        photoFileName = mergedPhoto,
        updatedAtEpochMs = maxOf(local.updatedAtEpochMs, incoming.updatedAtEpochMs),
      ).withSyncedPostings()

      mergedById[local.id] = merged
    }

    // 2. Add backup-only profiles and explicitly kept duplicates (retaining incoming ID)
    val toInsert = preview.toAdd + preview.duplicates
      .filter { it.incomingProfile.id in keepDuplicateIds }
      .map { it.incomingProfile }

    for (rawIncoming in toInsert) {
      val incoming = rawIncoming.withSyncedPostings()
      includedIncomingIds += incoming.id
      val photoName = incoming.photoFileName?.takeIf { name ->
        val bytes = backupPreview.photos[name]
        if (bytes != null && bytes.isNotEmpty()) {
          photosToWrite[name] = bytes
          photoAssignmentsByProfileId[incoming.id] = name
          true
        } else {
          false
        }
      }
      val normalized = incoming.copy(
        photoFileName = photoName,
        tags = DirectorySearch.normalizeTags(incoming.tags, mergedById.values.toList()),
      )
      mergedById[normalized.id] = normalized
    }

    val mergedProfiles = mergedById.values.toList()
    val validProfileIds = mergedProfiles.map { it.id }.toSet()

    // 3. Merge reusable groups & memberships without duplicates
    val mergedGroups = localGroups.toMutableList()
    for (incomingGroup in backupPreview.groups) {
      val eligibleMembers = incomingGroup.memberIds.filter {
        it in includedIncomingIds && it in validProfileIds
      }
      val existingIndex = mergedGroups.indexOfFirst {
        it.id == incomingGroup.id || it.name.equals(incomingGroup.name.trim(), ignoreCase = true)
      }
      if (existingIndex >= 0) {
        val existing = mergedGroups[existingIndex]
        val combinedMembers = (existing.memberIds + eligibleMembers).distinct()
        mergedGroups[existingIndex] = existing.copy(memberIds = combinedMembers)
      } else {
        mergedGroups += incomingGroup.copy(
          name = incomingGroup.name.trim(),
          memberIds = eligibleMembers.distinct(),
        )
      }
    }

    // 4. Owner card: keep local if a card exists; otherwise restore from backup when present
    val hasLocalCard = hasOwnerCardContent(localOwnerCard)
    val mergedOwnerCard = if (hasLocalCard) {
      localOwnerCard
    } else if (hasOwnerCardContent(backupPreview.ownerCard)) {
      backupPreview.ownerCard
    } else {
      localOwnerCard
    }

    val mergedCustomCategories = DirectorySearch.normalizeCategoryList(
      localCustomCategories +
        backupPreview.customCategories +
        mergedProfiles.mapNotNull { it.customCategory },
    )

    return MergeOutcome(
      mergedProfiles = mergedProfiles,
      mergedGroups = mergedGroups,
      mergedOwnerCard = mergedOwnerCard,
      photosToWrite = photosToWrite,
      photoAssignmentsByProfileId = photoAssignmentsByProfileId,
      mergedCustomCategories = mergedCustomCategories,
    )
  }

  fun hasOwnerCardContent(card: OwnerCard): Boolean =
    listOf(
      card.name,
      card.phone,
      card.email,
      card.organisation,
      card.designation,
      card.service,
      card.cadre,
      card.serviceBatch,
      card.foundationCourseBatch,
      card.trainingGroup,
    ).any { it.isNotBlank() }
}
