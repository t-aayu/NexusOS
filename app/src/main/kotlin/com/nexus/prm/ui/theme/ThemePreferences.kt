package com.nexus.prm.ui.theme

import android.content.Context
import android.os.Build
import com.nexus.prm.widget.QuickCaptureWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
  AMOLED("AMOLED"),
}

enum class AccentChoice(
  val label: String,
  val previewLightHex: Long,
  val previewDarkHex: Long,
) {
  SLATE_BLUE("Nexus Slate Blue", 0xFF3E5F82, 0xFFABC7EB),
  TEAL("Teal", 0xFF1E6B63, 0xFF7FD4C8),
  INDIGO("Indigo", 0xFF42539F, 0xFFB8C3FF),
  VIOLET("Violet", 0xFF6B4C9A, 0xFFD5B9FA),
  AMBER("Amber", 0xFF9A5B13, 0xFFF5BF74),
  WALLPAPER("Wallpaper colours", 0xFF3E5F82, 0xFFABC7EB),
}

data class AppearanceSettings(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val accentChoice: AccentChoice = AccentChoice.SLATE_BLUE,
)

object ThemePreferences {
  private const val PREFS_NAME = "nexus_theme_prefs"
  private const val KEY_THEME_MODE = "theme_mode"
  private const val KEY_ACCENT_CHOICE = "accent_choice"
  private const val KEY_USE_WALLPAPER_COLORS = "use_wallpaper_colors"

  private val sharedSettings = MutableStateFlow(AppearanceSettings())
  private var initialized = false

  fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  fun resolveSavedThemeMode(raw: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM

  fun resolveSavedAccentChoice(raw: String?, legacyWallpaperEnabled: Boolean): AccentChoice {
    val parsed = AccentChoice.entries.firstOrNull { it.name == raw }
    if (parsed != null) return parsed
    return if (legacyWallpaperEnabled) AccentChoice.WALLPAPER else AccentChoice.SLATE_BLUE
  }

  fun settingsFlow(context: Context): StateFlow<AppearanceSettings> {
    if (!initialized) {
      val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val mode = resolveSavedThemeMode(prefs.getString(KEY_THEME_MODE, null))
      val legacyWallpaper = prefs.getBoolean(KEY_USE_WALLPAPER_COLORS, false)
      val accent = resolveSavedAccentChoice(prefs.getString(KEY_ACCENT_CHOICE, null), legacyWallpaper)
      sharedSettings.value = AppearanceSettings(themeMode = mode, accentChoice = accent)
      initialized = true
    }
    return sharedSettings.asStateFlow()
  }

  fun currentSettings(context: Context): AppearanceSettings = settingsFlow(context).value

  fun setThemeMode(context: Context, mode: ThemeMode) {
    val appContext = context.applicationContext
    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_THEME_MODE, mode.name)
      .apply()
    val updated = settingsFlow(appContext).value.copy(themeMode = mode)
    sharedSettings.value = updated
    QuickCaptureWidgetProvider.refreshAllWidgets(appContext)
  }

  fun setAccentChoice(context: Context, choice: AccentChoice) {
    val appContext = context.applicationContext
    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_ACCENT_CHOICE, choice.name)
      .putBoolean(KEY_USE_WALLPAPER_COLORS, choice == AccentChoice.WALLPAPER)
      .apply()
    val updated = settingsFlow(appContext).value.copy(accentChoice = choice)
    sharedSettings.value = updated
    QuickCaptureWidgetProvider.refreshAllWidgets(appContext)
  }
}
