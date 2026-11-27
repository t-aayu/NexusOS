package com.nexus.prm.callerid

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import com.nexus.prm.MainActivity
import com.nexus.prm.R
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.ui.theme.ThemePreferences
import com.nexus.prm.ui.theme.resolveNexusColorScheme
import java.util.concurrent.atomic.AtomicBoolean

object CallerIdOverlayManager {
  private const val CHANNEL_ID = "nexus_caller_id"
  private const val NOTIFICATION_ID = 4107
  private const val AUTO_DISMISS_MS = 35_000L

  private val mainHandler = Handler(Looper.getMainLooper())
  private var currentView: View? = null
  private var autoDismissRunnable: Runnable? = null
  private val unlockPending = AtomicBoolean(false)

  fun showCallerCard(context: Context, profile: PersonProfile) {
    val appContext = context.applicationContext
    val name = profile.name.trim()
    if (name.isEmpty()) return
    val subtitle = CallerIdMatcher.formatCallerSubtitle(profile)

    mainHandler.post {
      dismiss(appContext)
      if (Settings.canDrawOverlays(appContext)) {
        val shown = showOverlayWindow(appContext, profile.id, name, subtitle)
        if (!shown) {
          showNotificationFallback(appContext, profile.id, name, subtitle)
        }
      } else {
        showNotificationFallback(appContext, profile.id, name, subtitle)
      }
      scheduleAutoDismiss(appContext)
    }
  }

  fun clearPendingUnlock() {
    unlockPending.set(false)
  }

  fun onCardTapped(context: Context, profileId: String) {
    val appContext = context.applicationContext
    val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (km != null && km.isKeyguardLocked) {
      if (!unlockPending.compareAndSet(false, true)) return
      appContext.startActivity(CallerIdUnlockActivity.createLaunchIntent(appContext, profileId))
    } else {
      dismiss(appContext)
      appContext.startActivity(
        Intent(appContext, MainActivity::class.java).apply {
          action = CallerIdUnlockActivity.ACTION_OPEN_CALLER_PROFILE
          putExtra(CallerIdUnlockActivity.EXTRA_PROFILE_ID, profileId)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
      )
    }
  }

  fun dismiss(context: Context) {
    val appContext = context.applicationContext
    autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
    autoDismissRunnable = null

    currentView?.let { view ->
      runCatching {
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.removeViewImmediate(view)
      }
      currentView = null
    }

    runCatching {
      val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      nm.cancel(NOTIFICATION_ID)
    }
  }

  private fun showOverlayWindow(
    context: Context,
    profileId: String,
    name: String,
    subtitle: String,
  ): Boolean {
    return runCatching {
      val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
      val root = LayoutInflater.from(context).inflate(R.layout.view_caller_id_card, null)

      val systemDark = (
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ) == Configuration.UI_MODE_NIGHT_YES
      val settings = ThemePreferences.currentSettings(context)
      val scheme = resolveNexusColorScheme(
        context = context,
        themeMode = settings.themeMode,
        accentChoice = settings.accentChoice,
        systemDark = systemDark,
      )
      (root.background as? GradientDrawable)?.apply {
        setColor(ColorUtils.setAlphaComponent(scheme.surfaceContainerLow.toArgb(), 0xEB))
        val strokePx = (1f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        setStroke(strokePx, ColorUtils.setAlphaComponent(scheme.primary.toArgb(), 0x66))
      }
      root.findViewById<TextView>(R.id.caller_id_brand)?.setTextColor(scheme.primary.toArgb())
      root.findViewById<TextView>(R.id.caller_id_name).apply {
        text = name
        setTextColor(scheme.onSurface.toArgb())
      }
      val subView = root.findViewById<TextView>(R.id.caller_id_subtitle)
      if (subtitle.isNotEmpty()) {
        subView.visibility = View.VISIBLE
        subView.text = subtitle
        subView.setTextColor(scheme.onSurfaceVariant.toArgb())
      } else {
        subView.visibility = View.GONE
      }
      root.setOnClickListener {
        onCardTapped(context, profileId)
      }
      root.findViewById<TextView>(R.id.caller_id_dismiss).apply {
        setTextColor(scheme.onSurface.toArgb())
        setOnClickListener { dismiss(context) }
      }

      val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
      }

      @Suppress("DEPRECATION")
      val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

      val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        windowType,
        flags,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = (48 * context.resources.displayMetrics.density).toInt()
      }

      wm.addView(root, params)
      currentView = root
      true
    }.getOrDefault(false)
  }

  private fun showNotificationFallback(
    context: Context,
    profileId: String,
    name: String,
    subtitle: String,
  ) {
    runCatching {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          "Nexus Caller ID",
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "Shows matching Nexus contact details during incoming calls"
          setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
      }

      val tapPendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        CallerIdUnlockActivity.createLaunchIntent(context, profileId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(name)
        .setSubText("Nexus Caller ID")
        .setContentIntent(tapPendingIntent)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(true)
        .setTimeoutAfter(AUTO_DISMISS_MS)

      if (subtitle.isNotEmpty()) {
        builder.setContentText(subtitle)
      }

      nm.notify(NOTIFICATION_ID, builder.build())
    }
  }

  private fun scheduleAutoDismiss(context: Context) {
    val runnable = Runnable { dismiss(context) }
    autoDismissRunnable = runnable
    mainHandler.postDelayed(runnable, AUTO_DISMISS_MS)
  }
}
