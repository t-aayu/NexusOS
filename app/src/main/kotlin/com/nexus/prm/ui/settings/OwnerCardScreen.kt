package com.nexus.prm.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnerCardScreen(
  repository: DirectoryRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val savedCard by repository.ownerCard.collectAsStateWithLifecycle()

  var card by remember { mutableStateOf(savedCard) }
  var initialized by remember { mutableStateOf(false) }
  var savedMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(savedCard) {
    if (!initialized) {
      card = savedCard
      initialized = true
    }
  }

  val preview = remember(card) { OwnerCardVCard.buildPreview(card) }
  val qrBitmap = remember(preview.vCardPayload) {
    OwnerCardVCard.generateQrBitmap(preview.vCardPayload)?.asImageBitmap()
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = NexusFrostedStyle.topAppBarColors(),
        title = { Text("My contact card", fontWeight = FontWeight.SemiBold) },
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
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "QR Share Preview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (qrBitmap != null) {
            androidx.compose.material3.Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color.White,
            ) {
              Image(
                bitmap = qrBitmap,
                contentDescription = "My contact card QR code",
                modifier = Modifier
                  .padding(12.dp)
                  .size(210.dp),
              )
            }
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              preview.lines.forEach { (label, value) ->
                Text(
                  text = "$label: $value",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = { ContactShareHelper.shareOwnerText(context, card) },
              ) {
                Icon(
                  imageVector = Icons.Outlined.Share,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Share text")
              }
              OutlinedButton(
                onClick = { ContactShareHelper.shareOwnerVCard(context, card) },
              ) {
                Text("Share vCard")
              }
              OutlinedButton(
                onClick = { ContactShareHelper.shareOwnerQrImage(context, card) },
              ) {
                Text("Share QR")
              }
            }
          } else {
            Text(
              text = "Enter and select at least one field below to generate your vCard QR code.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      savedMessage?.let { msg ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = msg,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = NexusFrostedStyle.borderStroke(MaterialTheme.colorScheme),
        colors = NexusFrostedStyle.cardColors(),
        elevation = NexusFrostedStyle.cardElevation(),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Fields & Sharing Selection",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
          ShareableFieldRow(
            label = "Name",
            value = card.name,
            checked = card.shareName,
            onValueChange = { card = card.copy(name = it); savedMessage = null },
            onCheckedChange = { card = card.copy(shareName = it); savedMessage = null },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          )
          ShareableFieldRow(
            label = "Phone",
            value = card.phone,
            checked = card.sharePhone,
            onValueChange = { card = card.copy(phone = it); savedMessage = null },
            onCheckedChange = { card = card.copy(sharePhone = it); savedMessage = null },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          )
          ShareableFieldRow(
            label = "Email",
            value = card.email,
            checked = card.shareEmail,
            onValueChange = { card = card.copy(email = it); savedMessage = null },
            onCheckedChange = { card = card.copy(shareEmail = it); savedMessage = null },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          )
          ShareableFieldRow(
            label = "Organisation",
            value = card.organisation,
            checked = card.shareOrganisation,
            onValueChange = { card = card.copy(organisation = it); savedMessage = null },
            onCheckedChange = { card = card.copy(shareOrganisation = it); savedMessage = null },
          )
          ShareableFieldRow(
            label = "Designation",
            value = card.designation,
            checked = card.shareDesignation,
            onValueChange = { card = card.copy(designation = it); savedMessage = null },
            onCheckedChange = { card = card.copy(shareDesignation = it); savedMessage = null },
          )
          ShareableFieldRow(
            label = "Service",
            value = card.service,
            checked = card.shareService,
            onValueChange = { card = card.copy(service = it); savedMessage = null },
            onCheckedChange = { card = card.copy(shareService = it); savedMessage = null },
          )
          ShareableFieldRow(
            label = "Cadre",
            value = card.cadre,
            checked = card.shareCadre,
            onValueChange = { card = card.copy(cadre = it); savedMessage = null },
            onCheckedChange = { card = card.copy(shareCadre = it); savedMessage = null },
          )
          ShareableFieldRow(
            label = "Service batch",
            value = card.serviceBatch,
            checked = card.shareServiceBatch,
            onValueChange = { card = card.copy(serviceBatch = it); savedMessage = null },
            onCheckedChange = { card = card.copy(shareServiceBatch = it); savedMessage = null },
          )
          ShareableFieldRow(
            label = "Foundation Course batch",
            value = card.foundationCourseBatch,
            checked = card.shareFoundationCourseBatch,
            onValueChange = { card = card.copy(foundationCourseBatch = it); savedMessage = null },
            onCheckedChange = { card = card.copy(shareFoundationCourseBatch = it); savedMessage = null },
          )
          ShareableFieldRow(
            label = "Training group",
            value = card.trainingGroup,
            checked = card.shareTrainingGroup,
            onValueChange = { card = card.copy(trainingGroup = it); savedMessage = null },
            onCheckedChange = { card = card.copy(shareTrainingGroup = it); savedMessage = null },
          )
        }
      }

      Button(
        onClick = {
          scope.launch {
            repository.saveOwnerCard(card)
            savedMessage = "My contact card saved."
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Save contact card")
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun ShareableFieldRow(
  label: String,
  value: String,
  checked: Boolean,
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
      onCheckedChange = onCheckedChange,
    )
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
      singleLine = true,
      keyboardOptions = keyboardOptions,
      modifier = Modifier.weight(1f),
    )
  }
}
