package com.nexus.prm.ui.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.DuplicateMatch
import com.nexus.prm.data.DuplicateMatcher
import com.nexus.prm.data.PersonCategory
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.PhotoUtils
import com.nexus.prm.ui.components.ProfileAvatar
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersonEditDraft(
  val sessionId: String,
  val profileId: String? = null,
  val isInitialized: Boolean = false,
  val loadedExisting: PersonProfile? = null,
  val name: String = "",
  val phone: String = "",
  val category: PersonCategory? = null,
  val whereWeMet: String = "",
  val email: String = "",
  val organisation: String = "",
  val designation: String = "",
  val service: String = "",
  val cadre: String = "",
  val serviceBatch: String = "",
  val foundationCourseBatch: String = "",
  val trainingGroup: String = "",
  val existingPhotoFileName: String? = null,
  val pendingPhotoTempPath: String? = null,
  val removePhoto: Boolean = false,
  val tags: List<String> = emptyList(),
  val tagInput: String = "",
  val moreDetailsExpanded: Boolean = false,
) {
  fun applySavedProfile(existing: PersonProfile): PersonEditDraft {
    if (isInitialized) return this
    return copy(
      isInitialized = true,
      loadedExisting = existing,
      name = existing.name,
      phone = existing.phone,
      category = existing.category,
      whereWeMet = existing.whereWeMet,
      email = existing.email,
      organisation = existing.organisation,
      designation = existing.designation,
      service = existing.service,
      cadre = existing.cadre,
      serviceBatch = existing.serviceBatch,
      foundationCourseBatch = existing.foundationCourseBatch,
      trainingGroup = existing.trainingGroup,
      existingPhotoFileName = existing.photoFileName,
      tags = existing.tags,
      moreDetailsExpanded = listOf(
        existing.email,
        existing.organisation,
        existing.designation,
        existing.service,
        existing.cadre,
        existing.serviceBatch,
        existing.foundationCourseBatch,
        existing.trainingGroup,
      ).any { it.isNotBlank() },
    )
  }

  companion object {
    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }

    fun initialFor(profileId: String?): PersonEditDraft = PersonEditDraft(
      sessionId = UUID.randomUUID().toString(),
      profileId = profileId,
      isInitialized = profileId == null,
    )

    val Saver: Saver<PersonEditDraft, String> = Saver(
      save = { draft -> json.encodeToString(draft) },
      restore = { encoded -> json.decodeFromString<PersonEditDraft>(encoded) },
    )
  }
}

object DraftPhotoStorage {
  fun writeTempPhoto(filesDir: File, sessionId: String, bytes: ByteArray): String {
    val dir = File(filesDir, "draft_photos")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "$sessionId.jpg")
    file.writeBytes(bytes)
    return file.absolutePath
  }

  fun readTempPhoto(path: String?): ByteArray? {
    if (path.isNullOrBlank()) return null
    val file = File(path)
    return if (file.exists()) runCatching { file.readBytes() }.getOrNull() else null
  }

  fun deleteTempPhoto(path: String?) {
    if (path.isNullOrBlank()) return
    runCatching { File(path).delete() }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonEditScreen(
  profileId: String?,
  repository: DirectoryRepository,
  onBack: () -> Unit,
  onSaved: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var draft by rememberSaveable(profileId, stateSaver = PersonEditDraft.Saver) {
    mutableStateOf(PersonEditDraft.initialFor(profileId))
  }

  val allProfiles by repository.profiles.collectAsStateWithLifecycle()
  val suggestedTags = remember(allProfiles) { DirectorySearch.distinctTags(allProfiles) }

  var pendingPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }
  LaunchedEffect(draft.pendingPhotoTempPath) {
    pendingPhotoBytes = withContext(Dispatchers.IO) {
      DraftPhotoStorage.readTempPhoto(draft.pendingPhotoTempPath)
    }
  }

  var nameError by remember { mutableStateOf<String?>(null) }
  var isSaving by remember { mutableStateOf(false) }
  var pendingDuplicate by remember { mutableStateOf<DuplicateMatch?>(null) }

  fun cancelAndCleanup() {
    DraftPhotoStorage.deleteTempPhoto(draft.pendingPhotoTempPath)
    onBack()
  }

  BackHandler(enabled = !isSaving) {
    cancelAndCleanup()
  }

  LaunchedEffect(profileId) {
    if (!draft.isInitialized && profileId != null) {
      val existing = repository.getProfile(profileId)
      draft = if (existing != null) {
        draft.applySavedProfile(existing)
      } else {
        draft.copy(isInitialized = true)
      }
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
    if (uri != null) {
      scope.launch {
        val tempPath = withContext(Dispatchers.IO) {
          val bytes = PhotoUtils.copyAndCompressFromUri(context, uri) ?: return@withContext null
          DraftPhotoStorage.writeTempPhoto(context.filesDir, draft.sessionId, bytes)
        }
        if (tempPath != null) {
          draft = draft.copy(pendingPhotoTempPath = tempPath, removePhoto = false)
        }
      }
    }
  }

  fun commitSave() {
    val trimmedName = draft.name.trim()
    if (trimmedName.isEmpty() || isSaving) return
    isSaving = true

    scope.launch {
      val now = System.currentTimeMillis()
      val base = draft.loadedExisting
      val tempPath = draft.pendingPhotoTempPath
      val pendingBytes = withContext(Dispatchers.IO) {
        DraftPhotoStorage.readTempPhoto(tempPath)
      }
      val profile = PersonProfile(
        id = base?.id ?: UUID.randomUUID().toString(),
        name = trimmedName,
        phone = draft.phone,
        category = draft.category,
        whereWeMet = draft.whereWeMet,
        email = draft.email,
        organisation = draft.organisation,
        designation = draft.designation,
        service = draft.service,
        cadre = draft.cadre,
        serviceBatch = draft.serviceBatch,
        foundationCourseBatch = draft.foundationCourseBatch,
        trainingGroup = draft.trainingGroup,
        tags = draft.tags,
        photoFileName = if (draft.removePhoto) null else draft.existingPhotoFileName,
        isFavourite = base?.isFavourite ?: false,
        createdAtEpochMs = base?.createdAtEpochMs ?: now,
        updatedAtEpochMs = now,
      )
      val saved = repository.saveProfile(
        profile = profile,
        pendingPhotoBytes = pendingBytes,
        removePhoto = draft.removePhoto,
      )
      withContext(Dispatchers.IO) {
        DraftPhotoStorage.deleteTempPhoto(tempPath)
      }
      isSaving = false
      onSaved(saved.id)
    }
  }

  fun performSave() {
    val trimmedName = draft.name.trim()
    if (trimmedName.isEmpty()) {
      nameError = "Name is required"
      return
    }
    if (isSaving) return
    val match = DuplicateMatcher.findMatch(
      phone = draft.phone,
      email = draft.email,
      candidates = allProfiles,
      excludeProfileId = profileId,
    )
    if (match != null) {
      pendingDuplicate = match
      return
    }
    commitSave()
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        title = { Text(if (profileId == null) "Add person" else "Edit person", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = ::cancelAndCleanup) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          TextButton(onClick = ::performSave, enabled = !isSaving && draft.isInitialized) {
            Text("Save", fontWeight = FontWeight.SemiBold)
          }
        },
      )
    },
  ) { innerPadding ->
    if (!draft.isInitialized) return@Scaffold

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val hasPhoto = (draft.pendingPhotoTempPath != null) || (!draft.removePhoto && draft.existingPhotoFileName != null)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ProfileAvatar(
          name = draft.name.ifBlank { "?" },
          photoFileName = if (draft.removePhoto) null else draft.existingPhotoFileName,
          loadPhotoBytes = repository::loadPhotoBytes,
          size = 72.dp,
          overridePhotoBytes = pendingPhotoBytes,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedButton(
            onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
          ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (hasPhoto) "Replace photo" else "Add photo")
          }
          if (hasPhoto) {
            TextButton(
              onClick = {
                DraftPhotoStorage.deleteTempPhoto(draft.pendingPhotoTempPath)
                draft = draft.copy(pendingPhotoTempPath = null, removePhoto = true)
              },
            ) {
              Text("Remove photo", color = MaterialTheme.colorScheme.error)
            }
          }
        }
      }

      OutlinedTextField(
        value = draft.name,
        onValueChange = {
          draft = draft.copy(name = it)
          if (nameError != null && it.isNotBlank()) nameError = null
        },
        label = { Text("Name *") },
        isError = nameError != null,
        supportingText = nameError?.let { msg -> { Text(msg) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = draft.phone,
        onValueChange = { draft = draft.copy(phone = it) },
        label = { Text("Phone") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
      )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Category (optional)",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          val categoryChipColors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
          )
          PersonCategory.entries.forEach { option ->
            val selected = draft.category == option
            FilterChip(
              selected = selected,
              colors = categoryChipColors,
              onClick = { draft = draft.copy(category = if (selected) null else option) },
              leadingIcon = if (selected) {
                { Icon(Icons.Filled.Check, contentDescription = null) }
              } else null,
              label = { Text(option.label) },
            )
          }
        }
      }

      OutlinedTextField(
        value = draft.whereWeMet,
        onValueChange = { draft = draft.copy(whereWeMet = it) },
        label = { Text("Where we met") },
        minLines = 2,
        maxLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
      )

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Tags",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = draft.tagInput,
            onValueChange = { draft = draft.copy(tagInput = it) },
            label = { Text("Add tag") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedButton(
            onClick = {
              val normalized = DirectorySearch.normalizeTags(draft.tags + draft.tagInput, allProfiles)
              draft = draft.copy(tags = normalized, tagInput = "")
            },
            enabled = draft.tagInput.isNotBlank(),
          ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add")
          }
        }
        if (draft.tags.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            draft.tags.forEach { tag ->
              InputChip(
                selected = true,
                onClick = {
                  draft = draft.copy(tags = draft.tags.filterNot { it.equals(tag, ignoreCase = true) })
                },
                label = { Text(tag) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $tag") },
              )
            }
          }
        }
        val unusedSuggestions = suggestedTags.filter { candidate ->
          draft.tags.none { it.equals(candidate, ignoreCase = true) }
        }
        if (unusedSuggestions.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            unusedSuggestions.take(8).forEach { suggestion ->
              SuggestionChip(
                onClick = {
                  draft = draft.copy(tags = DirectorySearch.normalizeTags(draft.tags + suggestion, allProfiles))
                },
                label = { Text("+ $suggestion") },
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { draft = draft.copy(moreDetailsExpanded = !draft.moreDetailsExpanded) }
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "More details",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Icon(
          imageVector = if (draft.moreDetailsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          contentDescription = if (draft.moreDetailsExpanded) "Collapse more details" else "Expand more details",
          tint = MaterialTheme.colorScheme.primary,
        )
      }

      AnimatedVisibility(visible = draft.moreDetailsExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = draft.email,
            onValueChange = { draft = draft.copy(email = it) },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = draft.organisation,
            onValueChange = { draft = draft.copy(organisation = it) },
            label = { Text("Organisation") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = draft.designation,
            onValueChange = { draft = draft.copy(designation = it) },
            label = { Text("Designation") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = draft.service,
            onValueChange = { draft = draft.copy(service = it) },
            label = { Text("Service") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = draft.cadre,
            onValueChange = { draft = draft.copy(cadre = it) },
            label = { Text("Cadre") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = draft.serviceBatch,
            onValueChange = { draft = draft.copy(serviceBatch = it) },
            label = { Text("Service batch") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = draft.foundationCourseBatch,
            onValueChange = { draft = draft.copy(foundationCourseBatch = it) },
            label = { Text("Foundation Course batch") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = draft.trainingGroup,
            onValueChange = { draft = draft.copy(trainingGroup = it) },
            label = { Text("Training group") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Button(
        onClick = ::performSave,
        enabled = !isSaving && draft.isInitialized,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (profileId == null) "Save person" else "Save changes")
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  pendingDuplicate?.let { match ->
    AlertDialog(
      onDismissRequest = { pendingDuplicate = null },
      title = { Text("Possible duplicate") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            "This matches ${match.existingProfile.name} on ${match.matchedField.label}.",
            fontWeight = FontWeight.Medium,
          )
          if (match.existingProfile.phone.isNotBlank()) {
            Text("Phone: ${match.existingProfile.phone}", style = MaterialTheme.typography.bodySmall)
          }
          if (match.existingProfile.email.isNotBlank()) {
            Text("Email: ${match.existingProfile.email}", style = MaterialTheme.typography.bodySmall)
          }
          Text(
            "You can skip saving this record or keep it as a separate profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            pendingDuplicate = null
            commitSave()
          },
        ) {
          Text("Keep separately")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = {
              pendingDuplicate = null
              cancelAndCleanup()
            },
          ) {
            Text("Skip")
          }
          TextButton(onClick = { pendingDuplicate = null }) {
            Text("Edit")
          }
        }
      },
    )
  }
}
