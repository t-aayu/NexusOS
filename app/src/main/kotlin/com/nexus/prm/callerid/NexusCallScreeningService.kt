package com.nexus.prm.callerid

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.nexus.prm.NexusApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusCallScreeningService : CallScreeningService() {

  override fun onScreenCall(callDetails: Call.Details) {
    // Respond immediately so directory lookup or card rendering never delays ringing.
    respondToCall(callDetails, CallResponse.Builder().build())

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
      callDetails.callDirection != Call.Details.DIRECTION_INCOMING
    ) {
      return
    }

    if (!CallerIdPreferences.isEnabled(this)) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !CallerIdPreferences.isRoleHeld(this)) {
      return
    }

    val incomingNumber = callDetails.handle?.schemeSpecificPart?.trim().orEmpty()
    if (incomingNumber.isEmpty()) return

    val app = applicationContext as? NexusApp ?: return
    // Use application-scoped work on IO so unbinding/destroying CallScreeningService
    // right after respondToCall never cancels cold-start directory load or overlay display.
    app.appScope.launch(Dispatchers.IO) {
      runCatching {
        val profiles = app.directoryRepository.profiles.value
        val match = CallerIdMatcher.findUnambiguousMatch(incomingNumber, profiles) ?: return@runCatching
        CallerIdOverlayManager.showCallerCard(app, match)
      }
    }
  }
}
