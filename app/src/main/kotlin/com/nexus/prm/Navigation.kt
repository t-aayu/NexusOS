package com.nexus.prm

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.nexus.prm.ui.groups.GroupsScreen
import com.nexus.prm.ui.imports.ContactImportScreen
import com.nexus.prm.ui.imports.CsvImportScreen
import com.nexus.prm.ui.imports.QuickImportReviewScreen
import com.nexus.prm.ui.people.PeopleScreen
import com.nexus.prm.ui.people.PeopleViewModel
import com.nexus.prm.ui.profile.PersonDetailScreen
import com.nexus.prm.ui.profile.PersonEditScreen
import com.nexus.prm.ui.settings.OwnerCardScreen
import com.nexus.prm.ui.settings.SettingsScreen

@Composable
fun MainNavigation(
  quickCaptureToken: Int = 0,
  callerProfileEvent: Pair<Int, String>? = null,
  quickImportEvent: Triple<Int, String?, String?>? = null,
) {
  val context = LocalContext.current
  val app = context.applicationContext as NexusApp
  val repository = app.directoryRepository
  val autoBackupManager = app.autoBackupManager
  val backStack = rememberNavBackStack(People)
  var handledQuickCaptureToken by rememberSaveable { mutableIntStateOf(0) }
  var handledCallerProfileToken by rememberSaveable { mutableIntStateOf(0) }
  var handledQuickImportToken by rememberSaveable { mutableIntStateOf(0) }

  LaunchedEffect(quickCaptureToken) {
    if (quickCaptureToken > handledQuickCaptureToken) {
      handledQuickCaptureToken = quickCaptureToken
      val top = backStack.lastOrNull()
      if (top !is PersonEdit || top.profileId != null) {
        backStack.add(PersonEdit(profileId = null, focusNameOnOpen = true))
      }
    }
  }

  LaunchedEffect(callerProfileEvent) {
    if (callerProfileEvent != null && callerProfileEvent.first > handledCallerProfileToken) {
      handledCallerProfileToken = callerProfileEvent.first
      val targetId = callerProfileEvent.second
      val top = backStack.lastOrNull()
      if (top !is PersonDetail || top.profileId != targetId) {
        backStack.add(PersonDetail(profileId = targetId))
      }
    }
  }

  LaunchedEffect(quickImportEvent) {
    if (quickImportEvent != null && quickImportEvent.first > handledQuickImportToken) {
      handledQuickImportToken = quickImportEvent.first
      backStack.add(
        QuickImportReview(
          initialText = quickImportEvent.second,
          vCardUriString = quickImportEvent.third,
        ),
      )
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
      entry<People> {
        val peopleViewModel: PeopleViewModel = viewModel { PeopleViewModel(repository) }
        PeopleScreen(
          viewModel = peopleViewModel,
          onAddClick = { backStack.add(PersonEdit(profileId = null)) },
          onPersonClick = { id -> backStack.add(PersonDetail(profileId = id)) },
          onSettingsClick = { backStack.add(Settings) },
          onOwnerCardClick = { backStack.add(OwnerCardRoute) },
          onImportContactsClick = { backStack.add(ContactImport) },
          onImportCsvClick = { backStack.add(CsvImport) },
          onImportVCardClick = { backStack.add(QuickImportReview(openFilePickerOnStart = true)) },
          onManageGroupsClick = { backStack.add(Groups) },
        )
      }
      entry<PersonDetail> { key ->
        PersonDetailScreen(
          profileId = key.profileId,
          repository = repository,
          onBack = { backStack.removeLastOrNull() },
          onEdit = { id -> backStack.add(PersonEdit(profileId = id)) },
        )
      }
      entry<PersonEdit> { key ->
        PersonEditScreen(
          profileId = key.profileId,
          focusNameOnOpen = key.focusNameOnOpen,
          repository = repository,
          onBack = { backStack.removeLastOrNull() },
          onSaved = { savedId ->
            backStack.removeLastOrNull()
            if (key.profileId == null) {
              backStack.add(PersonDetail(profileId = savedId))
            }
          },
        )
      }
      entry<Settings> {
        SettingsScreen(
          repository = repository,
          autoBackupManager = autoBackupManager,
          onBack = { backStack.removeLastOrNull() },
          onOpenOwnerCard = { backStack.add(OwnerCardRoute) },
        )
      }
      entry<ContactImport> {
        ContactImportScreen(
          repository = repository,
          onBack = { backStack.removeLastOrNull() },
          onImported = { summary ->
            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
            backStack.removeLastOrNull()
          },
        )
      }
      entry<CsvImport> {
        CsvImportScreen(
          repository = repository,
          onBack = { backStack.removeLastOrNull() },
          onImported = { summary ->
            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
            backStack.removeLastOrNull()
          },
        )
      }
      entry<QuickImportReview> { key ->
        QuickImportReviewScreen(
          repository = repository,
          initialText = key.initialText,
          vCardUriString = key.vCardUriString,
          openFilePickerOnStart = key.openFilePickerOnStart,
          onBack = { backStack.removeLastOrNull() },
          onSaved = { savedId ->
            backStack.removeLastOrNull()
            backStack.add(PersonDetail(profileId = savedId))
          },
        )
      }
      entry<Groups> {
        GroupsScreen(
          repository = repository,
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<OwnerCardRoute> {
        OwnerCardScreen(
          repository = repository,
          onBack = { backStack.removeLastOrNull() },
        )
      }
    },
  )
}
