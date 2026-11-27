package com.nexus.prm

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object People : NavKey

@Serializable data class PersonDetail(val profileId: String) : NavKey

@Serializable data class PersonEdit(val profileId: String? = null) : NavKey

@Serializable data object Settings : NavKey

@Serializable data object ContactImport : NavKey

@Serializable data object CsvImport : NavKey

@Serializable data object Groups : NavKey

@Serializable data object OwnerCardRoute : NavKey
