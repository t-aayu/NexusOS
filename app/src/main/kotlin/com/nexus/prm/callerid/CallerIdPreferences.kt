package com.nexus.prm.callerid

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

enum class CallerIdStatus(val label: String) {
  OFF("Off"),
  SETUP_REQUIRED("Setup required"),
  ACTIVE("Active"),
}

object CallerIdPreferences {
  private const val PREFS_NAME = "nexus_caller_id_prefs"
  private const val KEY_ENABLED = "caller_id_enabled"

  fun resolveStatus(enabled: Boolean, roleHeld: Boolean): CallerIdStatus = when {
    !enabled -> CallerIdStatus.OFF
    !roleHeld -> CallerIdStatus.SETUP_REQUIRED
    else -> CallerIdStatus.ACTIVE
  }

  fun isEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_ENABLED, false)

  fun setEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_ENABLED, enabled)
      .apply()
  }

  fun isRoleAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
  }

  fun isRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
  }

  fun createRequestRoleIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
    return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
  }
}
