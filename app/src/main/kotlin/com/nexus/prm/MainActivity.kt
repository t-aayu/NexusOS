package com.nexus.prm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.prm.callerid.CallerIdUnlockActivity
import com.nexus.prm.ui.theme.NexusTheme
import com.nexus.prm.ui.theme.ThemePreferences
import com.nexus.prm.widget.QuickCaptureWidgetProvider

class MainActivity : ComponentActivity() {
  private var quickCaptureSignal by mutableIntStateOf(0)
  private var callerProfileSignal by mutableStateOf<Pair<Int, String>?>(null)
  private var quickImportSignal by mutableStateOf<Triple<Int, String?, String?>?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      consumeLaunchIntent(intent)
    }

    enableEdgeToEdge()
    setContent {
      val useWallpaperColors by ThemePreferences.useWallpaperColorsFlow(this).collectAsStateWithLifecycle()
      NexusTheme(dynamicColor = useWallpaperColors) {
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

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    consumeLaunchIntent(intent)
  }

  private fun consumeLaunchIntent(incoming: Intent?) {
    when (incoming?.action) {
      QuickCaptureWidgetProvider.ACTION_QUICK_CAPTURE_ADD_PERSON -> {
        quickCaptureSignal += 1
        incoming.action = null
      }
      CallerIdUnlockActivity.ACTION_OPEN_CALLER_PROFILE -> {
        val profileId = incoming.getStringExtra(CallerIdUnlockActivity.EXTRA_PROFILE_ID)?.trim().orEmpty()
        if (profileId.isNotEmpty()) {
          val nextToken = (callerProfileSignal?.first ?: 0) + 1
          callerProfileSignal = nextToken to profileId
        }
        incoming.action = null
      }
      Intent.ACTION_PROCESS_TEXT -> {
        val selected = incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val nextToken = (quickImportSignal?.first ?: 0) + 1
        quickImportSignal = Triple(nextToken, selected ?: "", null)
        incoming.action = null
      }
      Intent.ACTION_SEND -> {
        val streamUri = readStreamUri(incoming)
        val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
        val nextToken = (quickImportSignal?.first ?: 0) + 1
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
          val nextToken = (quickImportSignal?.first ?: 0) + 1
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
}
