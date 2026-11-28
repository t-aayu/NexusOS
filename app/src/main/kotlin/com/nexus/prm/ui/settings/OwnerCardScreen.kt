package com.nexus.prm.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.data.ContactShareHelper
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.OwnerCard
import com.nexus.prm.data.OwnerCardVCard
import com.nexus.prm.ui.theme.NexusFrostedStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnerCardScreen(
  repository: DirectoryRepository,
  onBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  editing: Boolean = false,
  onEdit: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val savedCard by repository.ownerCard.collectAsStateWithLifecycle()

  var draft by rememberSaveable(stateSaver = OwnerCardDraftSaver) {
    mutableStateOf(if (editing) savedCard else null)
  }
  val card = draft ?: savedCard
  var saveFailed by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  val preview = remember(card) { OwnerCardVCard.buildPreview(card) }
  val qrBitmap = remember(editing, preview.vCardPayload) {
    if (editing) null else OwnerCardVCard.generateQrBitmap(preview.vCardPayload)?.asImageBitmap()
  }

  BackHandler(enabled = saving) {}

  fun save() {
    if (saving) return
    saving = true
    saveFailed = false
    scope.launch {
      try {
        repository.saveOwnerCard(card)
        onBack?.invoke()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        saveFailed = true
      } finally {
        saving = false
      }
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = NexusFrostedStyle.topAppBarColors(),
        title = { Text(if (editing) "Edit contact card" else "My contact card", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          onBack?.let { back ->
            IconButton(enabled = !saving, onClick = back) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        },
        actions = {
          if (editing) {
            TextButton(onClick = ::save, enabled = !saving) { Text("Save contact card") }
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (!editing) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            if (qrBitmap != null) {
              androidx.compose.material3.Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
              ) {
                Image(
                  bitmap = qrBitmap,
                  contentDescription = "My contact card QR code",
                  modifier = Modifier
                    .padding(12.dp)
                    .widthIn(max = 208.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                )
              }
            }
            if (preview.lines.isEmpty()) {
              Text(
                "Choose what to share",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                "Add details to your card and select the fields to include.",
                style = MaterialTheme.typography.bodyMedium,
              )
            } else {
              if (qrBitmap == null) {
                Text("This card is too large for a QR code. You can still share it as text or vCard.")
              }
              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                FilledTonalButton(onClick = { ContactShareHelper.shareOwnerText(context, card) }) {
                  Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.size(6.dp))
                  Text("Share text")
                }
                OutlinedButton(onClick = { ContactShareHelper.shareOwnerVCard(context, card) }) {
                  Text("Share vCard")
                }
                if (qrBitmap != null) {
                  OutlinedButton(onClick = { ContactShareHelper.shareOwnerQrImage(context, card) }) {
                    Text("Share QR")
                  }
                }
              }
            }
            if (onEdit != null) {
              TextButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(if (preview.lines.isEmpty()) "Set up contact card" else "Edit fields")
              }
            }
          }
        }
      }

      if (!editing && preview.lines.isNotEmpty()) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              "Included in your card",
              style = MaterialTheme.typography.titleMedium,
            )
            preview.lines.forEach { (label, value) ->
              Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(value, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }

      if (editing) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Fields to share",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              "Include the fields you want people to receive.",
              style = MaterialTheme.typography.bodyMedium,
            )
            if (saveFailed) {
              Text("Could not save your card. Your changes are still here.", color = MaterialTheme.colorScheme.error)
            }
            ShareableFieldRow(
              enabled = !saving,
              label = "Name",
              value = card.name,
              checked = card.shareName,
              onValueChange = { draft = card.copy(name = it) },
              onCheckedChange = { draft = card.copy(shareName = it) },
              keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            ShareableFieldRow(
              enabled = !saving,
              label = "Phone",
              value = card.phone,
              checked = card.sharePhone,
              onValueChange = { draft = card.copy(phone = it) },
              onCheckedChange = { draft = card.copy(sharePhone = it) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            ShareableFieldRow(
              enabled = !saving,
              label = "Email",
              value = card.email,
              checked = card.shareEmail,
              onValueChange = { draft = card.copy(email = it) },
              onCheckedChange = { draft = card.copy(shareEmail = it) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            ShareableFieldRow(
              enabled = !saving,
              label = "Organisation",
              value = card.organisation,
              checked = card.shareOrganisation,
              onValueChange = { draft = card.copy(organisation = it) },
              onCheckedChange = { draft = card.copy(shareOrganisation = it) },
            )
            ShareableFieldRow(
              enabled = !saving,
              label = "Designation",
              value = card.designation,
              checked = card.shareDesignation,
              onValueChange = { draft = card.copy(designation = it) },
              onCheckedChange = { draft = card.copy(shareDesignation = it) },
            )
            ShareableFieldRow(
              enabled = !saving,
              label = "Service",
              value = card.service,
              checked = card.shareService,
              onValueChange = { draft = card.copy(service = it) },
              onCheckedChange = { draft = card.copy(shareService = it) },
            )
            ShareableFieldRow(
              enabled = !saving,
              label = "Cadre",
              value = card.cadre,
              checked = card.shareCadre,
              onValueChange = { draft = card.copy(cadre = it) },
              onCheckedChange = { draft = card.copy(shareCadre = it) },
            )
            ShareableFieldRow(
              enabled = !saving,
              label = "Service batch",
              value = card.serviceBatch,
              checked = card.shareServiceBatch,
              onValueChange = { draft = card.copy(serviceBatch = it) },
              onCheckedChange = { draft = card.copy(shareServiceBatch = it) },
            )
            ShareableFieldRow(
              enabled = !saving,
              label = "Foundation Course batch",
              value = card.foundationCourseBatch,
              checked = card.shareFoundationCourseBatch,
              onValueChange = { draft = card.copy(foundationCourseBatch = it) },
              onCheckedChange = { draft = card.copy(shareFoundationCourseBatch = it) },
            )
            ShareableFieldRow(
              enabled = !saving,
              label = "Training group",
              value = card.trainingGroup,
              checked = card.shareTrainingGroup,
              onValueChange = { draft = card.copy(trainingGroup = it) },
              onCheckedChange = { draft = card.copy(shareTrainingGroup = it) },
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

private val OwnerCardDraftSaver = Saver<OwnerCard?, String>(
  save = { card -> card?.let { Json.encodeToString(it) } },
  restore = { Json.decodeFromString<OwnerCard>(it) },
)

@Composable
private fun ShareableFieldRow(
  label: String,
  value: String,
  checked: Boolean,
  enabled: Boolean,
  onValueChange: (String) -> Unit,
  onCheckedChange: (Boolean) -> Unit,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = checked,
      enabled = enabled,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.semantics { contentDescription = "Include $label when sharing" },
    )
    OutlinedTextField(
      value = value,
      enabled = enabled,
      onValueChange = onValueChange,
      label = { Text(label) },
      singleLine = true,
      keyboardOptions = keyboardOptions,
      modifier = Modifier.weight(1f),
    )
  }
}
