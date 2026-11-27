package com.nexus.prm.ui.imports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.CsvParseResult
import com.nexus.prm.data.CsvParsedRow
import com.nexus.prm.data.CsvRosterParser
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.DuplicateMatch
import com.nexus.prm.data.DuplicateMatcher
import com.nexus.prm.data.PersonProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CsvRowDecision(
  val row: CsvParsedRow,
  val included: Boolean = true,
  val keepIfDuplicate: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
  repository: DirectoryRepository,
  onBack: () -> Unit,
  onImported: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val existingProfiles by repository.profiles.collectAsStateWithLifecycle()

  var parseResult by remember { mutableStateOf<CsvParseResult?>(null) }
  var decisions by remember { mutableStateOf<List<CsvRowDecision>>(emptyList()) }
  var readError by remember { mutableStateOf<String?>(null) }
  var isSaving by remember { mutableStateOf(false) }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    readError = null
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
          } ?: error("Could not read CSV file")
        }
      }.onSuccess { text ->
        val parsed = CsvRosterParser.parse(text)
        parseResult = parsed
        decisions = parsed.validRows.map { CsvRowDecision(row = it) }
      }.onFailure { err ->
        readError = err.message ?: "Failed to open CSV file"
      }
    }
  }

  val duplicateMatches = remember(decisions, existingProfiles) {
    computeCsvBatchMatches(decisions, existingProfiles)
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
        title = { Text("Import CSV roster", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      val currentParse = parseResult
      if (currentParse == null) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text("UTF-8 CSV Template", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(
                text = "Headers: ${CsvRosterParser.EXPECTED_HEADERS.joinToString(",")}",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "Name is the only required column. Quoted commas and phone formatting are preserved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          readError?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error)
          }

          Button(
            onClick = { filePickerLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Choose CSV file")
          }
        }
      } else if (currentParse.fatalError != null) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = currentParse.fatalError,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
          )
          Button(
            onClick = { filePickerLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "*/*")) },
          ) {
            Text("Choose another file")
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "Preview: ${decisions.size} valid rows · ${currentParse.errors.size} errors · ${duplicateMatches.size} possible duplicates",
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }

          if (currentParse.errors.isNotEmpty()) {
            item {
              Text(
                text = "Row errors (${currentParse.errors.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
              )
            }
            items(currentParse.errors) { err ->
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              ) {
                Text(
                  text = "Line ${err.lineNumber}: ${err.reason}",
                  modifier = Modifier.padding(12.dp),
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }

          items(decisions.indices.toList(), key = { decisions[it].row.lineNumber }) { idx ->
            val decision = decisions[idx]
            val profile = decision.row.profile
            val match = duplicateMatches[idx]

            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Checkbox(
                    checked = decision.included,
                    onCheckedChange = { checked ->
                      decisions = decisions.toMutableList().also {
                        it[idx] = decision.copy(included = checked)
                      }
                    },
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Line ${decision.row.lineNumber}: ${profile.name}",
                      fontWeight = FontWeight.SemiBold,
                    )
                    val meta = listOfNotNull(
                      profile.phone.takeIf { it.isNotBlank() },
                      profile.email.takeIf { it.isNotBlank() },
                      profile.service.takeIf { it.isNotBlank() },
                      profile.trainingGroup.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                      Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }

                if (decision.included && match != null) {
                  Text(
                    text = "Possible duplicate: matches ${match.existingProfile.name} (${match.matchedField.label})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                      selected = !decision.keepIfDuplicate,
                      onClick = {
                        decisions = decisions.toMutableList().also {
                          it[idx] = decision.copy(keepIfDuplicate = false)
                        }
                      },
                      label = { Text("Skip") },
                    )
                    FilterChip(
                      selected = decision.keepIfDuplicate,
                      onClick = {
                        decisions = decisions.toMutableList().also {
                          it[idx] = decision.copy(keepIfDuplicate = true)
                        }
                      },
                      label = { Text("Keep separately") },
                    )
                  }
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = onBack,
            enabled = !isSaving,
            modifier = Modifier.weight(1f),
          ) {
            Text("Cancel")
          }
          Button(
            enabled = !isSaving && decisions.isNotEmpty(),
            modifier = Modifier.weight(1f),
            onClick = {
              isSaving = true
              scope.launch {
                val toSave = mutableListOf<PersonProfile>()
                var skipped = 0
                decisions.forEachIndexed { idx, decision ->
                  val match = duplicateMatches[idx]
                  if (!decision.included || (match != null && !decision.keepIfDuplicate)) {
                    skipped++
                  } else {
                    toSave += decision.row.profile
                  }
                }
                val saved = repository.saveProfilesBatch(toSave)
                isSaving = false
                onImported(
                  "CSV import complete: ${saved.size} saved, $skipped skipped, ${currentParse.errors.size} invalid.",
                )
              }
            },
          ) {
            Text("Import")
          }
        }
      }
    }
  }
}

private fun computeCsvBatchMatches(
  decisions: List<CsvRowDecision>,
  existingProfiles: List<PersonProfile>,
): Map<Int, DuplicateMatch> {
  val matches = mutableMapOf<Int, DuplicateMatch>()
  val runningCandidates = existingProfiles.toMutableList()
  decisions.forEachIndexed { idx, decision ->
    if (!decision.included) return@forEachIndexed
    val profile = decision.row.profile
    val match = DuplicateMatcher.findMatch(
      phone = profile.phone,
      email = profile.email,
      candidates = runningCandidates,
    )
    if (match != null) {
      matches[idx] = match
    }
    if (match == null || decision.keepIfDuplicate) {
      runningCandidates += profile
    }
  }
  return matches
}
