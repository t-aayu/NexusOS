package com.nexus.prm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.nexus.prm.MainActivity
import com.nexus.prm.R
import com.nexus.prm.ui.theme.ThemePreferences
import com.nexus.prm.ui.theme.resolveNexusColorScheme

class QuickCaptureWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
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

    for (appWidgetId in appWidgetIds) {
      val views = RemoteViews(context.packageName, R.layout.widget_quick_capture)
      views.setInt(R.id.widget_bg_image, "setColorFilter", scheme.surfaceContainerLow.toArgb())
      views.setInt(R.id.widget_button_bg_image, "setColorFilter", scheme.primary.toArgb())
      views.setTextColor(R.id.widget_brand, scheme.primary.toArgb())
      views.setTextColor(R.id.widget_subtitle, scheme.onSurface.toArgb())
      views.setTextColor(R.id.widget_add_button_label, scheme.onPrimary.toArgb())

      val launchIntent = Intent(context, MainActivity::class.java).apply {
        action = ACTION_QUICK_CAPTURE_ADD_PERSON
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_SINGLE_TOP or
          Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
      views.setOnClickPendingIntent(R.id.widget_add_button, pendingIntent)
      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }

  companion object {
    const val ACTION_QUICK_CAPTURE_ADD_PERSON = "com.nexus.prm.action.QUICK_CAPTURE_ADD_PERSON"

    fun refreshAllWidgets(context: Context) {
      runCatching {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(
          ComponentName(context, QuickCaptureWidgetProvider::class.java),
        )
        if (ids.isNotEmpty()) {
          QuickCaptureWidgetProvider().onUpdate(context, manager, ids)
        }
      }
    }
  }
}
