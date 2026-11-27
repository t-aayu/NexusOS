package com.nexus.prm.callerid

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.nexus.prm.MainActivity

class CallerIdUnlockActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)?.trim().orEmpty()
    if (profileId.isEmpty()) {
      CallerIdOverlayManager.clearPendingUnlock()
      finish()
      return
    }

    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (keyguardManager == null || !keyguardManager.isKeyguardLocked) {
      openProfileAndFinish(profileId)
      return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      keyguardManager.requestDismissKeyguard(
        this,
        object : KeyguardManager.KeyguardDismissCallback() {
          override fun onDismissSucceeded() {
            openProfileAndFinish(profileId)
          }

          override fun onDismissCancelled() {
            CallerIdOverlayManager.clearPendingUnlock()
            finish()
          }

          override fun onDismissError() {
            CallerIdOverlayManager.clearPendingUnlock()
            finish()
          }
        },
      )
    } else {
      CallerIdOverlayManager.clearPendingUnlock()
      finish()
    }
  }

  override fun onDestroy() {
    if (isFinishing) {
      CallerIdOverlayManager.clearPendingUnlock()
    }
    super.onDestroy()
  }

  private fun openProfileAndFinish(profileId: String) {
    CallerIdOverlayManager.clearPendingUnlock()
    CallerIdOverlayManager.dismiss(this)
    startActivity(
      Intent(this, MainActivity::class.java).apply {
        action = ACTION_OPEN_CALLER_PROFILE
        putExtra(EXTRA_PROFILE_ID, profileId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_SINGLE_TOP or
          Intent.FLAG_ACTIVITY_CLEAR_TOP
      },
    )
    finish()
  }

  companion object {
    const val ACTION_OPEN_CALLER_PROFILE = "com.nexus.prm.action.OPEN_CALLER_PROFILE"
    const val EXTRA_PROFILE_ID = "extra_caller_profile_id"

    fun createLaunchIntent(context: Context, profileId: String): Intent =
      Intent(context, CallerIdUnlockActivity::class.java).apply {
        putExtra(EXTRA_PROFILE_ID, profileId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
      }
  }
}
