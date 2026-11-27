package com.nexus.prm.callerid

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nexus.prm.MainActivity
import com.nexus.prm.R
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.ui.theme.AccentChoice
import com.nexus.prm.ui.theme.ThemeMode
import com.nexus.prm.ui.theme.resolveNexusColorScheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private const val CARD_HORIZONTAL_MARGIN_DP = 20f
private const val CARD_MAX_WIDTH_DP = 420f
private const val CARD_SURFACE_ALPHA = 0xFA

internal fun computeCallerCardWidthPx(usableWidthPx: Int, density: Float): Int {
  val marginPx = (CARD_HORIZONTAL_MARGIN_DP * density).roundToInt()
  val maxWidthPx = (CARD_MAX_WIDTH_DP * density).roundToInt()
  return minOf((usableWidthPx - 2 * marginPx).coerceAtLeast(0), maxWidthPx)
}

internal fun systemCallerCardThemeMode(systemDark: Boolean): ThemeMode =
  if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT

internal fun clampCallerCardPosition(
  x: Int,
  y: Int,
  cardWidth: Int,
  cardHeight: Int,
  usableLeft: Int,
  usableTop: Int,
  usableRight: Int,
  usableBottom: Int,
  horizontalMarginPx: Int,
  verticalMarginPx: Int,
): Pair<Int, Int> {
  val minX = usableLeft + horizontalMarginPx
  val maxX = (usableRight - horizontalMarginPx - cardWidth).coerceAtLeast(minX)
  val minY = usableTop + verticalMarginPx
  val maxY = (usableBottom - verticalMarginPx - cardHeight).coerceAtLeast(minY)
  return x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY)
}

internal fun selectFittingTags(
  tagWidthsPx: List<Int>,
  overflowChipWidthProvider: (remaining: Int) -> Int,
  gapPx: Int,
  maxRowWidthPx: Int,
): Pair<Int, Int> {
  val total = tagWidthsPx.size
  if (total == 0) return 0 to 0
  val allWidth = tagWidthsPx.sum() + (total - 1) * gapPx
  if (allWidth <= maxRowWidthPx) return total to 0

  for (visible in (total - 1) downTo 1) {
    val remaining = total - visible
    val chipsWidth = tagWidthsPx.take(visible).sum()
    val overflowWidth = overflowChipWidthProvider(remaining)
    val used = chipsWidth + visible * gapPx + overflowWidth
    if (used <= maxRowWidthPx) {
      return visible to remaining
    }
  }
  return 1 to (total - 1)
}

object CallerIdOverlayManager {
  private const val CHANNEL_ID = "nexus_caller_id"
  private const val NOTIFICATION_ID = 4107
  private const val AUTO_DISMISS_MS = 35_000L

  private val mainHandler = Handler(Looper.getMainLooper())
  private var currentView: View? = null
  private var autoDismissRunnable: Runnable? = null
  private var activeCallStateCallback: Any? = null
  private val unlockPending = AtomicBoolean(false)

  fun showCallerCard(context: Context, profile: PersonProfile) {
    val appContext = context.applicationContext
    val name = profile.name.trim()
    if (name.isEmpty()) return
    val subtitle = CallerIdMatcher.formatCallerSubtitle(profile)
    val tags = profile.tags.map { it.trim() }.filter { it.isNotEmpty() }

    mainHandler.post {
      dismiss(appContext)
      if (Settings.canDrawOverlays(appContext)) {
        val shown = showOverlayWindow(appContext, profile.id, name, subtitle, tags)
        if (!shown) {
          showNotificationFallback(appContext, profile.id, name, subtitle)
        }
      } else {
        showNotificationFallback(appContext, profile.id, name, subtitle)
      }
      registerCallEndListener(appContext)
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
    unregisterCallEndListener(appContext)

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
    tags: List<String>,
  ): Boolean {
    return runCatching {
      val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
      val root = LayoutInflater.from(context).inflate(R.layout.view_caller_id_card, null)
      val density = context.resources.displayMetrics.density

      val systemDark = (
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ) == Configuration.UI_MODE_NIGHT_YES
      val scheme = resolveNexusColorScheme(
        context = context,
        themeMode = systemCallerCardThemeMode(systemDark),
        accentChoice = AccentChoice.SLATE_BLUE,
        systemDark = systemDark,
      )
      (root.background as? GradientDrawable)?.apply {
        setColor(ColorUtils.setAlphaComponent(scheme.surfaceContainerLow.toArgb(), CARD_SURFACE_ALPHA))
        val strokePx = (1f * density).toInt().coerceAtLeast(1)
        setStroke(strokePx, ColorUtils.setAlphaComponent(scheme.outlineVariant.toArgb(), 0xCC))
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

      val baseTopPx = (48f * density).roundToInt()
      val usableLeft: Int
      val usableTop: Int
      val usableRight: Int
      val usableBottom: Int
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = wm.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
          WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        usableLeft = insets.left
        usableTop = insets.top
        usableRight = (metrics.bounds.width() - insets.right).coerceAtLeast(usableLeft)
        usableBottom = (metrics.bounds.height() - insets.bottom).coerceAtLeast(usableTop)
      } else {
        usableLeft = 0
        usableTop = 0
        usableRight = context.resources.displayMetrics.widthPixels
        usableBottom = context.resources.displayMetrics.heightPixels
      }

      val usableWidthPx = (usableRight - usableLeft).coerceAtLeast(0)
      val cardWidthPx = computeCallerCardWidthPx(usableWidthPx, density)
      val horizontalMarginPx = (CARD_HORIZONTAL_MARGIN_DP * density).roundToInt()
      val verticalMarginPx = (8f * density).roundToInt()

      val tagsRow = root.findViewById<LinearLayout>(R.id.caller_id_tags_row)
      populateTagChips(
        context = context,
        tagsRow = tagsRow,
        tags = tags,
        maxRowWidthPx = (cardWidthPx - (28f * density).roundToInt()).coerceAtLeast(0),
        chipBgColor = scheme.primaryContainer.toArgb(),
        chipTextColor = scheme.onPrimaryContainer.toArgb(),
        density = density,
      )

      val initialX = usableLeft + ((usableWidthPx - cardWidthPx) / 2).coerceAtLeast(0)
      val initialY = maxOf(baseTopPx, usableTop + verticalMarginPx)

      val params = WindowManager.LayoutParams(
        cardWidthPx,
        WindowManager.LayoutParams.WRAP_CONTENT,
        windowType,
        flags,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = initialX
        y = initialY
      }

      root.findViewById<TextView>(R.id.caller_id_dismiss).apply {
        setTextColor(scheme.onSurface.toArgb())
        setOnClickListener { dismiss(context) }
      }

      val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
      var downRawX = 0f
      var downRawY = 0f
      var startX = 0
      var startY = 0
      var isDragging = false

      root.setOnTouchListener { view, event ->
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> {
            downRawX = event.rawX
            downRawY = event.rawY
            startX = params.x
            startY = params.y
            isDragging = false
            true
          }
          MotionEvent.ACTION_MOVE -> {
            val dx = event.rawX - downRawX
            val dy = event.rawY - downRawY
            if (!isDragging && (dx * dx + dy * dy) > touchSlop * touchSlop) {
              isDragging = true
            }
            if (isDragging) {
              val cardHeightPx = view.height.takeIf { it > 0 } ?: (120f * density).roundToInt()
              val (clampedX, clampedY) = clampCallerCardPosition(
                x = (startX + dx).roundToInt(),
                y = (startY + dy).roundToInt(),
                cardWidth = cardWidthPx,
                cardHeight = cardHeightPx,
                usableLeft = usableLeft,
                usableTop = usableTop,
                usableRight = usableRight,
                usableBottom = usableBottom,
                horizontalMarginPx = horizontalMarginPx,
                verticalMarginPx = verticalMarginPx,
              )
              params.x = clampedX
              params.y = clampedY
              runCatching { wm.updateViewLayout(view, params) }
            }
            true
          }
          MotionEvent.ACTION_UP -> {
            if (!isDragging) {
              onCardTapped(context, profileId)
            }
            isDragging = false
            true
          }
          MotionEvent.ACTION_CANCEL -> {
            isDragging = false
            true
          }
          else -> false
        }
      }

      wm.addView(root, params)
      currentView = root
      true
    }.getOrDefault(false)
  }

  private fun populateTagChips(
    context: Context,
    tagsRow: LinearLayout?,
    tags: List<String>,
    maxRowWidthPx: Int,
    chipBgColor: Int,
    chipTextColor: Int,
    density: Float,
  ) {
    if (tagsRow == null) return
    tagsRow.removeAllViews()
    if (tags.isEmpty() || maxRowWidthPx <= 0) {
      tagsRow.visibility = View.GONE
      return
    }
    tagsRow.visibility = View.VISIBLE
    val gapPx = (6f * density).roundToInt()
    val measureSpecW = View.MeasureSpec.makeMeasureSpec(maxRowWidthPx, View.MeasureSpec.AT_MOST)
    val measureSpecH = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    val chipViews = tags.map { label ->
      createTagChipView(context, label, chipBgColor, chipTextColor, density).also {
        it.measure(measureSpecW, measureSpecH)
      }
    }
    val (visibleCount, remaining) = selectFittingTags(
      tagWidthsPx = chipViews.map { it.measuredWidth },
      overflowChipWidthProvider = { rem ->
        val overflowChip = createTagChipView(context, "+$rem", chipBgColor, chipTextColor, density)
        overflowChip.measure(measureSpecW, measureSpecH)
        overflowChip.measuredWidth
      },
      gapPx = gapPx,
      maxRowWidthPx = maxRowWidthPx,
    )

    val overflowChip = if (remaining > 0) {
      createTagChipView(context, "+$remaining", chipBgColor, chipTextColor, density).also {
        it.measure(measureSpecW, measureSpecH)
      }
    } else null

    for (i in 0 until visibleCount) {
      val chip = chipViews[i]
      if (visibleCount == 1 && overflowChip != null) {
        chip.maxWidth = (maxRowWidthPx - gapPx - overflowChip.measuredWidth).coerceAtLeast(0)
      }
      val lp = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
      )
      if (i < visibleCount - 1 || overflowChip != null) {
        lp.marginEnd = gapPx
      }
      tagsRow.addView(chip, lp)
    }

    if (overflowChip != null) {
      tagsRow.addView(
        overflowChip,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
      )
    }
  }

  private fun createTagChipView(
    context: Context,
    label: String,
    bgColor: Int,
    textColor: Int,
    density: Float,
  ): TextView = TextView(context).apply {
    text = label
    textSize = 11f
    setTextColor(textColor)
    isSingleLine = true
    ellipsize = TextUtils.TruncateAt.END
    val padH = (8f * density).roundToInt()
    val padV = (2f * density).roundToInt()
    setPadding(padH, padV, padH, padV)
    background = GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 999f * density
      setColor(bgColor)
    }
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

  private fun registerCallEndListener(context: Context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
    runCatching {
      var sawRinging = false
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
          override fun onCallStateChanged(state: Int) {
            if (state == TelephonyManager.CALL_STATE_RINGING) {
              sawRinging = true
            } else if (sawRinging && (state == TelephonyManager.CALL_STATE_IDLE || state == TelephonyManager.CALL_STATE_OFFHOOK)) {
              mainHandler.post { dismiss(context) }
            }
          }
        }
        tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
        activeCallStateCallback = callback
      } else {
        @Suppress("DEPRECATION")
        val listener = object : PhoneStateListener() {
          @Deprecated("Deprecated in Java")
          override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            if (state == TelephonyManager.CALL_STATE_RINGING) {
              sawRinging = true
            } else if (sawRinging && (state == TelephonyManager.CALL_STATE_IDLE || state == TelephonyManager.CALL_STATE_OFFHOOK)) {
              mainHandler.post { dismiss(context) }
            }
          }
        }
        @Suppress("DEPRECATION")
        tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        activeCallStateCallback = listener
      }
    }
  }

  private fun unregisterCallEndListener(context: Context) {
    val registered = activeCallStateCallback ?: return
    activeCallStateCallback = null
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && registered is TelephonyCallback) {
        tm.unregisterTelephonyCallback(registered)
      } else if (registered is PhoneStateListener) {
        @Suppress("DEPRECATION")
        tm.listen(registered, PhoneStateListener.LISTEN_NONE)
      }
    }
  }

  private fun scheduleAutoDismiss(context: Context) {
    val runnable = Runnable { dismiss(context) }
    autoDismissRunnable = runnable
    mainHandler.postDelayed(runnable, AUTO_DISMISS_MS)
  }
}
