package com.nexus.prm

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.callerid.CallerIdUnlockActivity
import com.nexus.prm.ui.theme.NexusTheme
import com.nexus.prm.ui.theme.ThemePreferences
import com.nexus.prm.ui.theme.isEffectiveDark
import com.nexus.prm.widget.QuickCaptureWidgetProvider

class MainActivity : ComponentActivity() {
  private var launchSequence = 0
  private var quickCaptureSignal by mutableIntStateOf(0)
  private var callerProfileSignal by mutableStateOf<Pair<Int, String>?>(null)
  private var quickImportSignal by mutableStateOf<Triple<Int, String?, String?>?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    launchSequence = savedInstanceState?.getInt(LAUNCH_SEQUENCE_KEY) ?: 0
    if (savedInstanceState == null) {
      consumeLaunchIntent(intent)
    }

    enableEdgeToEdge()
    setContent {
      val appearance by ThemePreferences.settingsFlow(this).collectAsStateWithLifecycle()
      val systemDark = isSystemInDarkTheme()
      val effectiveDark = isEffectiveDark(appearance.themeMode, systemDark)

      SideEffect {
        val barStyle = if (effectiveDark) {
          SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
          SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
      }

      NexusTheme(
        themeMode = appearance.themeMode,
        accentChoice = appearance.accentChoice,
        systemDark = systemDark,
      ) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          MainNavigation(
            quickCaptureToken = quickCaptureSignal,
            callerProfileEvent = callerProfileSignal,
            quickImportEvent = quickImportSignal,
          )
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putInt(LAUNCH_SEQUENCE_KEY, launchSequence)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    consumeLaunchIntent(intent)
  }

  private fun consumeLaunchIntent(incoming: Intent?) {
    when (incoming?.action) {
      QuickCaptureWidgetProvider.ACTION_QUICK_CAPTURE_ADD_PERSON -> {
        quickCaptureSignal = ++launchSequence
        incoming.action = null
      }
      CallerIdUnlockActivity.ACTION_OPEN_CALLER_PROFILE -> {
        val profileId = incoming.getStringExtra(CallerIdUnlockActivity.EXTRA_PROFILE_ID)?.trim().orEmpty()
        if (profileId.isNotEmpty()) {
          val nextToken = ++launchSequence
          callerProfileSignal = nextToken to profileId
        }
        incoming.action = null
      }
      Intent.ACTION_PROCESS_TEXT -> {
        val selected = incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val nextToken = ++launchSequence
        quickImportSignal = Triple(nextToken, selected ?: "", null)
        incoming.action = null
      }
      Intent.ACTION_SEND -> {
        val streamUri = readStreamUri(incoming)
        val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
        val nextToken = ++launchSequence
        if (streamUri != null) {
          quickImportSignal = Triple(nextToken, null, streamUri.toString())
        } else {
          quickImportSignal = Triple(nextToken, sharedText ?: "", null)
        }
        incoming.action = null
      }
      Intent.ACTION_VIEW -> {
        val dataUri = incoming.data
        if (dataUri != null) {
          val nextToken = ++launchSequence
          quickImportSignal = Triple(nextToken, null, dataUri.toString())
          incoming.action = null
        }
      }
    }
  }

  private fun readStreamUri(intent: Intent): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

  private companion object {
    const val LAUNCH_SEQUENCE_KEY = "launch_sequence"
  }
}
