package com.nexus.prm

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object People : NavKey

@Serializable data class PersonDetail(val profileId: String) : NavKey

@Serializable data class PersonEdit(
  val profileId: String? = null,
  val focusNameOnOpen: Boolean = false,
) : NavKey

@Serializable data object Settings : NavKey

@Serializable data object ContactImport : NavKey

@Serializable data object CsvImport : NavKey

@Serializable data class QuickImportReview(
  val initialText: String? = null,
  val vCardUriString: String? = null,
  val openFilePickerOnStart: Boolean = false,
) : NavKey

@Serializable data object Groups : NavKey

@Serializable data object OwnerCardRoute : NavKey

@Serializable data object OwnerCardEditRoute : NavKey

@Serializable data class GroupMembers(val groupId: String) : NavKey
