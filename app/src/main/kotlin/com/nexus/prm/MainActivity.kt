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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nexus.prm.ui.theme.NexusTheme
import com.nexus.prm.widget.QuickCaptureWidgetProvider

class MainActivity : ComponentActivity() {
  private var quickCaptureSignal by mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      consumeQuickCaptureIntent(intent)
    }

    enableEdgeToEdge()
    setContent {
      NexusTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          MainNavigation(quickCaptureToken = quickCaptureSignal)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    consumeQuickCaptureIntent(intent)
  }

  private fun consumeQuickCaptureIntent(incoming: Intent?) {
    if (incoming?.action == QuickCaptureWidgetProvider.ACTION_QUICK_CAPTURE_ADD_PERSON) {
      quickCaptureSignal += 1
      incoming.action = null
    }
  }
}
