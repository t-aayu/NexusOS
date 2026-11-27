package com.nexus.prm.callerid

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.nexus.prm.NexusApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NexusCallScreeningService : CallScreeningService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    serviceScope.launch {
      runCatching {
        val profiles = (applicationContext as NexusApp).directoryRepository.profiles.value
        val match = CallerIdMatcher.findUnambiguousMatch(incomingNumber, profiles) ?: return@runCatching
        CallerIdOverlayManager.showCallerCard(applicationContext, match)
      }
    }
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }
}
