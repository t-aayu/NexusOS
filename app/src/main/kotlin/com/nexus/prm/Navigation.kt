package com.nexus.prm

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.nexus.prm.ui.groups.GroupsScreen
import com.nexus.prm.ui.imports.ContactImportScreen
import com.nexus.prm.ui.imports.CsvImportScreen
import com.nexus.prm.ui.people.PeopleScreen
import com.nexus.prm.ui.people.PeopleViewModel
import com.nexus.prm.ui.profile.PersonDetailScreen
import com.nexus.prm.ui.profile.PersonEditScreen
import com.nexus.prm.ui.settings.OwnerCardScreen
import com.nexus.prm.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val context = LocalContext.current
  val app = context.applicationContext as NexusApp
  val repository = app.directoryRepository
  val autoBackupManager = app.autoBackupManager
  val backStack = rememberNavBackStack(People)

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
          onImportContactsClick = { backStack.add(ContactImport) },
          onImportCsvClick = { backStack.add(CsvImport) },
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
