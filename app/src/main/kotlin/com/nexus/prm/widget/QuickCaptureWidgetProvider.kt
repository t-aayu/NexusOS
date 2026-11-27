package com.nexus.prm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nexus.prm.MainActivity
import com.nexus.prm.R

class QuickCaptureWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    for (appWidgetId in appWidgetIds) {
      val views = RemoteViews(context.packageName, R.layout.widget_quick_capture)
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
  }
}
