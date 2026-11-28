package com.nexus.prm.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexus.prm.callerid.CallerIdPreferences
import com.nexus.prm.callerid.CallerIdStatus
import com.nexus.prm.ui.theme.NexusFrostedStyle
import kotlinx.coroutines.launch

@Composable
internal fun CallerIdSettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val lifecycleOwner = LocalLifecycleOwner.current
  var callerIdEnabled by remember { mutableStateOf(CallerIdPreferences.isEnabled(context)) }
  var callerIdRoleAvailable by remember {
    mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || CallerIdPreferences.isRoleAvailable(context))
  }
  var callerIdRoleHeld by remember {
    mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || CallerIdPreferences.isRoleHeld(context))
  }
  var hasContactsPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
    )
  }
  var hasOverlayPermission by remember {
    mutableStateOf(AndroidSettings.canDrawOverlays(context))
  }

  fun refreshCallerIdState() {
    callerIdRoleAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || CallerIdPreferences.isRoleAvailable(context)
    callerIdRoleHeld = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || CallerIdPreferences.isRoleHeld(context)
    callerIdEnabled = CallerIdPreferences.isEnabled(context)
    hasContactsPermission =
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    hasOverlayPermission = AndroidSettings.canDrawOverlays(context)
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        refreshCallerIdState()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val callerIdRoleLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) {
    val held = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || CallerIdPreferences.isRoleHeld(context)
    refreshCallerIdState()
    if (held) {
      errorMessage = null
      statusMessage = "Nexus Caller ID active."
    } else {
      statusMessage = null
      errorMessage = "Caller ID role was not granted. Status is Setup required."
    }
  }

  val contactsPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasContactsPermission = granted
  }

  val overlayPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) {
    hasOverlayPermission = AndroidSettings.canDrawOverlays(context)
  }

  SettingsPage(
    title = "Caller ID",
    onBack = onBack,
    statusMessage = statusMessage,
    errorMessage = errorMessage,
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      border = NexusFrostedStyle.borderStroke(MaterialTheme.colorScheme),
      colors = NexusFrostedStyle.cardColors(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        val callerIdStatus = CallerIdPreferences.resolveStatus(
          enabled = callerIdEnabled,
          roleHeld = callerIdRoleHeld,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Nexus Caller ID",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "Show a compact Nexus caller card while incoming calls ring.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = callerIdEnabled,
            enabled = callerIdRoleAvailable,
            onCheckedChange = { turnOn ->
              CallerIdPreferences.setEnabled(context, turnOn)
              refreshCallerIdState()
              if (!turnOn) {
                statusMessage = "Nexus Caller ID turned off."
              } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !CallerIdPreferences.isRoleHeld(context)) {
                val roleIntent = CallerIdPreferences.createRequestRoleIntent(context)
                if (roleIntent != null) {
                  callerIdRoleLauncher.launch(roleIntent)
                } else {
                  errorMessage = "Caller ID role is unavailable on this device."
                }
              } else {
                statusMessage = "Nexus Caller ID active."
              }
            },
          )
        }

        Text(
          text = when {
            !callerIdRoleAvailable -> "Status: Unavailable on this device"
            callerIdStatus == CallerIdStatus.ACTIVE -> "Status: Active — matching Nexus contacts appear while ringing."
            callerIdStatus == CallerIdStatus.SETUP_REQUIRED -> "Status: Setup required — grant Nexus the Caller ID role to show caller cards."
            else -> "Status: Off"
          },
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = when {
            !callerIdRoleAvailable || callerIdStatus == CallerIdStatus.SETUP_REQUIRED -> MaterialTheme.colorScheme.error
            callerIdStatus == CallerIdStatus.ACTIVE -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
        )

        if (callerIdStatus == CallerIdStatus.SETUP_REQUIRED && callerIdRoleAvailable) {
          OutlinedButton(
            onClick = {
              val roleIntent = CallerIdPreferences.createRequestRoleIntent(context)
              if (roleIntent != null) {
                callerIdRoleLauncher.launch(roleIntent)
              } else {
                errorMessage = "Caller ID role is unavailable on this device."
              }
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Grant Caller ID role")
          }
        }

        Text(
          text = if (callerIdStatus == CallerIdStatus.ACTIVE && !hasContactsPermission) {
            "Contacts permission is missing: callers already saved in Android Contacts may not get a Nexus card. Nexus-only numbers can still be matched."
          } else {
            "Contacts permission is needed for calls from numbers already saved in Android Contacts. Nexus-only numbers can still be matched without it."
          },
          style = MaterialTheme.typography.bodySmall,
          color = if (callerIdStatus == CallerIdStatus.ACTIVE && !hasContactsPermission) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )

        if (!hasContactsPermission) {
          OutlinedButton(
            onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Grant Contacts permission")
          }
        }

        if (callerIdStatus == CallerIdStatus.ACTIVE && !hasOverlayPermission) {
          OutlinedButton(
            onClick = {
              val intent = Intent(
                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
              )
              overlayPermissionLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Allow floating card over Phone screen")
          }
        }
      }
  }
  }
}
