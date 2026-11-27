package com.nexus.prm

import android.content.Intent
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
import com.nexus.prm.callerid.CallerIdUnlockActivity
import com.nexus.prm.ui.theme.NexusTheme
import com.nexus.prm.widget.QuickCaptureWidgetProvider

class MainActivity : ComponentActivity() {
  private var quickCaptureSignal by mutableIntStateOf(0)
  private var callerProfileSignal by mutableStateOf<Pair<Int, String>?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      consumeLaunchIntent(intent)
    }

    enableEdgeToEdge()
    setContent {
      NexusTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          MainNavigation(
            quickCaptureToken = quickCaptureSignal,
            callerProfileEvent = callerProfileSignal,
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
    }
  }
}
