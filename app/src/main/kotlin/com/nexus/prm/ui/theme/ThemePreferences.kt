package com.nexus.prm.ui.theme

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemePreferences {
  private const val PREFS_NAME = "nexus_theme_prefs"
  private const val KEY_USE_WALLPAPER_COLORS = "use_wallpaper_colors"

  private val _useWallpaperColors = MutableStateFlow<Boolean?>(null)

  fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  fun isUseWallpaperColors(context: Context): Boolean {
    val cached = _useWallpaperColors.value
    if (cached != null) return cached
    val stored = context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_USE_WALLPAPER_COLORS, false)
    _useWallpaperColors.value = stored
    return stored
  }

  fun observeUseWallpaperColors(context: Context): StateFlow<Boolean> {
    val initial = isUseWallpaperColors(context)
    return if (_useWallpaperColors.value != null) {
      MutableStateFlow(initial).also {
        // Keep a shared flow synchronized below
      }
    } else {
      MutableStateFlow(false)
    }
  }

  private val sharedFlow = MutableStateFlow(false)
  private var initialized = false

  fun useWallpaperColorsFlow(context: Context): StateFlow<Boolean> {
    if (!initialized) {
      sharedFlow.value = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_USE_WALLPAPER_COLORS, false)
      initialized = true
    }
    return sharedFlow.asStateFlow()
  }

  fun setUseWallpaperColors(context: Context, enabled: Boolean) {
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_USE_WALLPAPER_COLORS, enabled)
      .apply()
    initialized = true
    sharedFlow.value = enabled
    _useWallpaperColors.value = enabled
  }
}
