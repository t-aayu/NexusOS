package com.nexus.prm

import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.nexus.prm.ui.groups.GroupsScreen
import com.nexus.prm.ui.groups.ManageGroupMembersDialog
import com.nexus.prm.ui.imports.ContactImportScreen
import com.nexus.prm.ui.imports.CsvImportScreen
import com.nexus.prm.ui.imports.QuickImportReviewScreen
import com.nexus.prm.ui.people.PeopleScreen
import com.nexus.prm.ui.people.PeopleViewModel
import com.nexus.prm.ui.profile.PersonDetailScreen
import com.nexus.prm.ui.profile.PersonEditScreen
import com.nexus.prm.ui.settings.AppearanceScreen
import com.nexus.prm.ui.settings.BackupSettingsScreen
import com.nexus.prm.ui.settings.CallerIdSettingsScreen
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
  val peopleViewModel: PeopleViewModel = viewModel { PeopleViewModel(repository) }
  val peopleListState = rememberLazyListState()
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

  val selectedTab = when {
    backStack.size == 1 -> DirectoryTab.PEOPLE
    backStack.size == 2 && backStack.lastOrNull() == Groups -> DirectoryTab.GROUPS
    backStack.size == 2 && backStack.lastOrNull() == OwnerCardRoute -> DirectoryTab.MY_CARD
    else -> null
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      if (selectedTab != null) {
        NavigationBar {
          DirectoryTab.entries.forEach { tab ->
            NavigationBarItem(
              selected = selectedTab == tab,
              onClick = {
                if (tab != selectedTab) {
                  if (backStack.size > 1) backStack.removeLastOrNull()
                  if (tab.route != People) backStack.add(tab.route)
                }
              },
              icon = { Icon(tab.icon, contentDescription = null) },
              label = { Text(tab.label) },
            )
          }
        }
      }
    },
  ) { padding ->
    NavDisplay(
      backStack = backStack,
      modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
      onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
      entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
      ),
      entryProvider = entryProvider {
        entry<People> {
          PeopleScreen(
            viewModel = peopleViewModel,
            onAddClick = { backStack.add(PersonEdit(profileId = null)) },
            onPersonClick = { id -> backStack.add(PersonDetail(profileId = id)) },
            onEditClick = { id -> backStack.add(PersonEdit(profileId = id)) },
            onSettingsClick = { backStack.add(Settings) },
            onImportContactsClick = { backStack.add(ContactImport) },
            onImportCsvClick = { backStack.add(CsvImport) },
            onImportVCardClick = { backStack.add(QuickImportReview(openFilePickerOnStart = true)) },
            listState = peopleListState,
          )
        }
        entry<PersonDetail> { key ->
          PersonDetailScreen(
            profileId = key.profileId,
            repository = repository,
            onBack = { backStack.removeLastOrNull() },
            onEdit = { id -> backStack.add(PersonEdit(profileId = id)) },
            onOpenProfile = { id -> backStack.add(PersonDetail(profileId = id)) },
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
            onBack = { backStack.removeLastOrNull() },
            onOpenBackups = { backStack.add(BackupSettings) },
            onOpenAppearance = { backStack.add(AppearanceSettings) },
            onOpenCallerId = { backStack.add(CallerIdSettings) },
            onOpenOwnerCard = { backStack.add(OwnerCardRoute) },
          )
        }
        entry<BackupSettings> {
          BackupSettingsScreen(
            repository = repository,
            autoBackupManager = autoBackupManager,
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<AppearanceSettings> {
          AppearanceScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<CallerIdSettings> {
          CallerIdSettingsScreen(onBack = { backStack.removeLastOrNull() })
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
            onOpenGroup = { id -> backStack.add(GroupMembers(id)) },
          )
        }
        entry<GroupMembers> { key ->
          val groups by repository.groups.collectAsStateWithLifecycle()
          val group = groups.firstOrNull { it.id == key.groupId }
          if (group == null) {
            LaunchedEffect(key) {
              if (backStack.lastOrNull() == key) backStack.removeLastOrNull()
            }
          } else {
            val groupViewModel: PeopleViewModel = viewModel {
              PeopleViewModel(repository, groupId = key.groupId)
            }
            var showMembersDialog by rememberSaveable { mutableStateOf(false) }
            PeopleScreen(
              viewModel = groupViewModel,
              onPersonClick = { id -> backStack.add(PersonDetail(profileId = id)) },
              onEditClick = { id -> backStack.add(PersonEdit(profileId = id)) },
              group = group,
              onBack = { backStack.removeLastOrNull() },
              onManageMembers = { showMembersDialog = true },
            )
            if (showMembersDialog) {
              ManageGroupMembersDialog(
                repository = repository,
                group = group,
                onDismiss = { showMembersDialog = false },
              )
            }
          }
        }
        entry<OwnerCardRoute> {
          OwnerCardScreen(
            repository = repository,
            onBack = if (backStack.getOrNull(1) == OwnerCardRoute) null else {
              { backStack.removeLastOrNull() }
            },
            onEdit = { backStack.add(OwnerCardEditRoute) },
          )
        }
        entry<OwnerCardEditRoute> {
          OwnerCardScreen(
            repository = repository,
            editing = true,
            onBack = { backStack.removeLastOrNull() },
          )
        }
      },
    )
  }
}

private enum class DirectoryTab(
  val label: String,
  val route: NavKey,
  val icon: ImageVector,
) {
  PEOPLE("People", People, Icons.Outlined.Person),
  GROUPS("Groups", Groups, Icons.Outlined.Group),
  MY_CARD("My Card", OwnerCardRoute, Icons.Outlined.ContactPage),
}
