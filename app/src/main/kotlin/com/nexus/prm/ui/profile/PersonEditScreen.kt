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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.DirectorySearch
import com.nexus.prm.data.DuplicateMatch
import com.nexus.prm.data.DuplicateMatcher
import com.nexus.prm.data.LabeledContactValue
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
data class ContactRowDraft(
  val rowId: String = UUID.randomUUID().toString(),
  val value: String = "",
  val label: String = LabeledContactValue.DEFAULT_LABEL,
  val isPrimary: Boolean = false,
)

@Serializable
data class PersonEditDraft(
  val sessionId: String,
  val profileId: String? = null,
  val isInitialized: Boolean = false,
  val loadedExisting: PersonProfile? = null,
  val name: String = "",
  val phone: String = "",
  val phoneRows: List<ContactRowDraft> = listOf(ContactRowDraft(isPrimary = true)),
  val category: PersonCategory? = null,
  val customCategory: String? = null,
  val categoryQuery: String = "",
  val whereWeMet: String = "",
  val email: String = "",
  val emailRows: List<ContactRowDraft> = listOf(ContactRowDraft(isPrimary = true)),
  val organisation: String = "",
  val designation: String = "",
  val service: String = "",
  val cadre: String = "",
  val serviceBatch: String = "",
  val foundationCourseBatch: String = "",
  val trainingGroup: String = "",
  val otherDetails: String = "",
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
      phone = existing.primaryPhone(),
      phoneRows = ensureTrailingEmptyRow(
        existing.resolvedPhones().map {
          ContactRowDraft(value = it.value, label = it.label, isPrimary = it.isPrimary)
        },
      ),
      category = existing.category,
      customCategory = existing.customCategory,
      categoryQuery = existing.categoryLabel().orEmpty(),
      whereWeMet = existing.whereWeMet,
      email = existing.primaryEmail(),
      emailRows = ensureTrailingEmptyRow(
        existing.resolvedEmails().map {
          ContactRowDraft(value = it.value, label = it.label, isPrimary = it.isPrimary)
        },
      ),
      organisation = existing.organisation,
      designation = existing.designation,
      service = existing.service,
      cadre = existing.cadre,
      serviceBatch = existing.serviceBatch,
      foundationCourseBatch = existing.foundationCourseBatch,
      trainingGroup = existing.trainingGroup,
      otherDetails = existing.otherDetails,
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

    fun ensureTrailingEmptyRow(rows: List<ContactRowDraft>): List<ContactRowDraft> {
      val nonBlank = rows.filter { it.value.isNotBlank() }
      val primaryIdx = nonBlank.indexOfFirst { it.isPrimary }.takeIf { it >= 0 } ?: 0
      val normalizedNonBlank = nonBlank.mapIndexed { idx, row ->
        row.copy(isPrimary = idx == primaryIdx)
      }
      val existingTrailing = rows.lastOrNull()?.takeIf { it.value.isBlank() }
        ?: ContactRowDraft(isPrimary = normalizedNonBlank.isEmpty())
      return normalizedNonBlank + existingTrailing.copy(isPrimary = normalizedNonBlank.isEmpty())
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
  focusNameOnOpen: Boolean = false,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val nameFocusRequester = remember { FocusRequester() }
  var hasFocusedName by rememberSaveable(profileId) { mutableStateOf(false) }

  var draft by rememberSaveable(profileId, stateSaver = PersonEditDraft.Saver) {
    mutableStateOf(PersonEditDraft.initialFor(profileId))
  }

  val allProfiles by repository.profiles.collectAsStateWithLifecycle()
  val savedCustomCategories by repository.customCategories.collectAsStateWithLifecycle()
  val allCategoryOptions = remember(savedCustomCategories, allProfiles) {
    DirectorySearch.allCategoryOptions(savedCustomCategories + allProfiles.mapNotNull { it.customCategory })
  }
  val suggestedTags = remember(allProfiles) { DirectorySearch.distinctTags(allProfiles) }
  var categoryExpanded by remember { mutableStateOf(false) }

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

  LaunchedEffect(profileId, draft.isInitialized, focusNameOnOpen) {
    if (!draft.isInitialized && profileId != null) {
      val existing = repository.getProfile(profileId)
      draft = if (existing != null) {
        draft.applySavedProfile(existing)
      } else {
        draft.copy(isInitialized = true)
      }
    }
    if (focusNameOnOpen && draft.isInitialized && !hasFocusedName) {
      hasFocusedName = true
      nameFocusRequester.requestFocus()
      keyboardController?.show()
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
      val matchedSelectedLabel = draft.categoryQuery.trim().takeIf { it.isNotEmpty() }?.let { typed ->
        allCategoryOptions.firstOrNull { it.equals(typed, ignoreCase = true) }
      }
      val resolvedBuiltIn = matchedSelectedLabel?.let { PersonCategory.fromLabel(it) } ?: draft.category
      val resolvedCustom = when {
        resolvedBuiltIn != null -> null
        matchedSelectedLabel != null -> matchedSelectedLabel
        else -> draft.customCategory
      }
      val normalizedPhones = PersonProfile.normalizeContactList(
        raw = draft.phoneRows.map { LabeledContactValue(it.value, it.label, it.isPrimary) },
        normalizer = DuplicateMatcher::normalizePhone,
      )
      val normalizedEmails = PersonProfile.normalizeContactList(
        raw = draft.emailRows.map { LabeledContactValue(it.value, it.label, it.isPrimary) },
        normalizer = DuplicateMatcher::normalizeEmail,
      )
      val profile = PersonProfile(
        id = base?.id ?: UUID.randomUUID().toString(),
        name = trimmedName,
        phone = normalizedPhones.firstOrNull { it.isPrimary }?.value.orEmpty(),
        phones = normalizedPhones,
        category = resolvedBuiltIn,
        customCategory = resolvedCustom,
        whereWeMet = draft.whereWeMet,
        email = normalizedEmails.firstOrNull { it.isPrimary }?.value.orEmpty(),
        emails = normalizedEmails,
        organisation = draft.organisation,
        designation = draft.designation,
        service = draft.service,
        cadre = draft.cadre,
        serviceBatch = draft.serviceBatch,
        foundationCourseBatch = draft.foundationCourseBatch,
        trainingGroup = draft.trainingGroup,
        otherDetails = draft.otherDetails,
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
      phones = draft.phoneRows.map { it.value },
      emails = draft.emailRows.map { it.value },
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
          Button(
            onClick = ::performSave,
            enabled = !isSaving && draft.isInitialized,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.padding(end = 8.dp),
          ) {
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
        .consumeWindowInsets(innerPadding)
        .imePadding()
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
        keyboardOptions = KeyboardOptions(
          capitalization = KeyboardCapitalization.Words,
          imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(nameFocusRequester)
          .bringIntoViewOnFocus(),
      )

      MultiContactSection(
        title = "Phone numbers",
        fieldLabel = "Phone",
        rows = draft.phoneRows,
        keyboardType = KeyboardType.Phone,
        onRowsChanged = { updated ->
          val synced = PersonEditDraft.ensureTrailingEmptyRow(updated)
          draft = draft.copy(
            phoneRows = synced,
            phone = synced.firstOrNull { it.value.isNotBlank() && it.isPrimary }?.value.orEmpty(),
          )
        },
        onNext = { focusManager.moveFocus(FocusDirection.Down) },
      )

      MultiContactSection(
        title = "Email addresses",
        fieldLabel = "Email",
        rows = draft.emailRows,
        keyboardType = KeyboardType.Email,
        onRowsChanged = { updated ->
          val synced = PersonEditDraft.ensureTrailingEmptyRow(updated)
          draft = draft.copy(
            emailRows = synced,
            email = synced.firstOrNull { it.value.isNotBlank() && it.isPrimary }?.value.orEmpty(),
          )
        },
        onNext = { focusManager.moveFocus(FocusDirection.Down) },
      )

      val trimmedCategoryQuery = draft.categoryQuery.trim()
      val filteredCategories = remember(allCategoryOptions, trimmedCategoryQuery) {
        if (trimmedCategoryQuery.isEmpty()) {
          allCategoryOptions
        } else {
          allCategoryOptions.filter { it.contains(trimmedCategoryQuery, ignoreCase = true) }
        }
      }
      val hasExactCategoryMatch = trimmedCategoryQuery.isNotEmpty() &&
        allCategoryOptions.any { it.equals(trimmedCategoryQuery, ignoreCase = true) }

      ExposedDropdownMenuBox(
        expanded = categoryExpanded,
        onExpandedChange = { categoryExpanded = it },
      ) {
        OutlinedTextField(
          value = draft.categoryQuery,
          onValueChange = { typed ->
            val exact = allCategoryOptions.firstOrNull { it.equals(typed.trim(), ignoreCase = true) }
            val builtIn = exact?.let { PersonCategory.fromLabel(it) }
            draft = draft.copy(
              categoryQuery = typed,
              category = builtIn,
              customCategory = if (builtIn == null) exact else null,
            )
            categoryExpanded = true
          },
          label = { Text("Category (optional)") },
          singleLine = true,
          trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (draft.categoryQuery.isNotEmpty()) {
                IconButton(
                  onClick = {
                    draft = draft.copy(categoryQuery = "", category = null, customCategory = null)
                    categoryExpanded = true
                  },
                ) {
                  Icon(Icons.Filled.Close, contentDescription = "Clear category")
                }
              }
              ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
            }
          },
          colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
          modifier = Modifier
            .fillMaxWidth()
            .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
        )
        ExposedDropdownMenu(
          expanded = categoryExpanded,
          onDismissRequest = { categoryExpanded = false },
        ) {
          filteredCategories.forEach { option ->
            DropdownMenuItem(
              text = { Text(option) },
              trailingIcon = if (draft.categoryQuery.trim().equals(option, ignoreCase = true)) {
                { Icon(Icons.Filled.Check, contentDescription = null) }
              } else null,
              onClick = {
                val builtIn = PersonCategory.fromLabel(option)
                draft = draft.copy(
                  categoryQuery = option,
                  category = builtIn,
                  customCategory = if (builtIn == null) option else null,
                )
                categoryExpanded = false
              },
            )
          }
          if (trimmedCategoryQuery.isNotEmpty() && !hasExactCategoryMatch) {
            DropdownMenuItem(
              text = {
                Text(
                  text = "Create \u201C$trimmedCategoryQuery\u201D",
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Add,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              },
              onClick = {
                categoryExpanded = false
                scope.launch {
                  val savedLabel = repository.addCustomCategory(trimmedCategoryQuery)
                  val builtIn = PersonCategory.fromLabel(savedLabel)
                  draft = draft.copy(
                    categoryQuery = savedLabel,
                    category = builtIn,
                    customCategory = if (builtIn == null) savedLabel else null,
                  )
                }
              },
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
        modifier = Modifier
          .fillMaxWidth()
          .bringIntoViewOnFocus(),
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier
              .weight(1f)
              .bringIntoViewOnFocus(),
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
          val nextFocusAction = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
          OutlinedTextField(
            value = draft.organisation,
            onValueChange = { draft = draft.copy(organisation = it) },
            label = { Text("Organisation") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = nextFocusAction,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
          )
          OutlinedTextField(
            value = draft.designation,
            onValueChange = { draft = draft.copy(designation = it) },
            label = { Text("Designation") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = nextFocusAction,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
          )
          OutlinedTextField(
            value = draft.service,
            onValueChange = { draft = draft.copy(service = it) },
            label = { Text("Service") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
            keyboardActions = nextFocusAction,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
          )
          OutlinedTextField(
            value = draft.cadre,
            onValueChange = { draft = draft.copy(cadre = it) },
            label = { Text("Cadre") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = nextFocusAction,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
          )
          OutlinedTextField(
            value = draft.serviceBatch,
            onValueChange = { draft = draft.copy(serviceBatch = it) },
            label = { Text("Service batch") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFocusAction,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
          )
          OutlinedTextField(
            value = draft.foundationCourseBatch,
            onValueChange = { draft = draft.copy(foundationCourseBatch = it) },
            label = { Text("Foundation Course batch") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFocusAction,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
          )
          OutlinedTextField(
            value = draft.trainingGroup,
            onValueChange = { draft = draft.copy(trainingGroup = it) },
            label = { Text("Training group") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = nextFocusAction,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
          )
        }
      }

      OutlinedTextField(
        value = draft.otherDetails,
        onValueChange = { draft = draft.copy(otherDetails = it) },
        label = { Text("Other details") },
        minLines = 3,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
          .fillMaxWidth()
          .bringIntoViewOnFocus(),
      )

      Spacer(modifier = Modifier.height(8.dp))
      Button(
        onClick = ::performSave,
        enabled = !isSaving && draft.isInitialized,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (profileId == null) "Save person" else "Save changes")
      }
      Spacer(modifier = Modifier.height(24.dp))
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

private fun Modifier.bringIntoViewOnFocus(): Modifier = composed {
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  val scope = rememberCoroutineScope()
  this
    .bringIntoViewRequester(bringIntoViewRequester)
    .onFocusEvent { focusState ->
      if (focusState.isFocused) {
        scope.launch {
          bringIntoViewRequester.bringIntoView()
        }
      }
    }
}

@Composable
private fun MultiContactSection(
  title: String,
  fieldLabel: String,
  rows: List<ContactRowDraft>,
  keyboardType: KeyboardType,
  onRowsChanged: (List<ContactRowDraft>) -> Unit,
  onNext: () -> Unit,
) {
  var customLabelTargetRowId by remember { mutableStateOf<String?>(null) }
  var customLabelInput by remember { mutableStateOf("") }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    rows.forEachIndexed { index, row ->
      androidx.compose.runtime.key(row.rowId) {
        var labelMenuExpanded by remember { mutableStateOf(false) }
        val isNonBlank = row.value.isNotBlank()
        val isOnlyTrailingEmpty = !isNonBlank && index == rows.lastIndex

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = row.value,
              onValueChange = { newValue ->
                val updated = rows.map {
                  if (it.rowId == row.rowId) it.copy(value = newValue) else it
                }
                onRowsChanged(updated)
              },
              label = { Text(if (isOnlyTrailingEmpty) "Add $fieldLabel" else fieldLabel) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next,
              ),
              keyboardActions = KeyboardActions(onNext = { onNext() }),
              modifier = Modifier
                .weight(1f)
                .bringIntoViewOnFocus(),
            )
            if (isNonBlank) {
              IconButton(
                onClick = {
                  val updated = rows.map {
                    it.copy(isPrimary = it.rowId == row.rowId)
                  }
                  onRowsChanged(updated)
                },
              ) {
                Icon(
                  imageVector = if (row.isPrimary) Icons.Filled.Star else Icons.Outlined.StarOutline,
                  contentDescription = if (row.isPrimary) "Primary $fieldLabel" else "Mark primary $fieldLabel",
                  tint = if (row.isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = {
                  val remaining = rows.filterNot { it.rowId == row.rowId }
                  onRowsChanged(remaining)
                },
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "Remove $fieldLabel",
                )
              }
            }
          }

          if (isNonBlank) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              androidx.compose.foundation.layout.Box {
                AssistChip(
                  onClick = { labelMenuExpanded = true },
                  label = { Text(row.label) },
                )
                DropdownMenu(
                  expanded = labelMenuExpanded,
                  onDismissRequest = { labelMenuExpanded = false },
                ) {
                  LabeledContactValue.PRESET_LABELS.forEach { preset ->
                    DropdownMenuItem(
                      text = { Text(preset) },
                      onClick = {
                        labelMenuExpanded = false
                        onRowsChanged(
                          rows.map { if (it.rowId == row.rowId) it.copy(label = preset) else it },
                        )
                      },
                    )
                  }
                  DropdownMenuItem(
                    text = { Text("Custom\u2026") },
                    onClick = {
                      labelMenuExpanded = false
                      customLabelInput = if (row.label in LabeledContactValue.PRESET_LABELS) "" else row.label
                      customLabelTargetRowId = row.rowId
                    },
                  )
                }
              }
              if (row.isPrimary) {
                Text(
                  text = "Primary",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }
      }
    }
  }

  customLabelTargetRowId?.let { targetId ->
    AlertDialog(
      onDismissRequest = { customLabelTargetRowId = null },
      title = { Text("Custom label") },
      text = {
        OutlinedTextField(
          value = customLabelInput,
          onValueChange = { customLabelInput = it },
          label = { Text("Label") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val cleaned = customLabelInput.trim().ifEmpty { LabeledContactValue.DEFAULT_LABEL }
            onRowsChanged(
              rows.map { if (it.rowId == targetId) it.copy(label = cleaned) else it },
            )
            customLabelTargetRowId = null
          },
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { customLabelTargetRowId = null }) {
          Text("Cancel")
        }
      },
    )
  }
}
