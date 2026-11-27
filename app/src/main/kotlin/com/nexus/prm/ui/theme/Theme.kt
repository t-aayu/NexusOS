package com.nexus.prm.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class AccentPalette(
  val primary: Color,
  val onPrimary: Color,
  val primaryContainer: Color,
  val onPrimaryContainer: Color,
)

private fun accentPaletteFor(choice: AccentChoice, isDark: Boolean): AccentPalette =
  when (choice) {
    AccentChoice.SLATE_BLUE, AccentChoice.WALLPAPER -> if (isDark) {
      AccentPalette(primaryDark, onPrimaryDark, primaryContainerDark, onPrimaryContainerDark)
    } else {
      AccentPalette(primaryLight, onPrimaryLight, primaryContainerLight, onPrimaryContainerLight)
    }
    AccentChoice.TEAL -> if (isDark) {
      AccentPalette(Color(0xFF7FD4C8), Color(0xFF08332E), Color(0xFF1C4A44), Color(0xFFD9F4F0))
    } else {
      AccentPalette(Color(0xFF1E6B63), Color(0xFFFFFFFF), Color(0xFFD0EFEA), Color(0xFF0B2926))
    }
    AccentChoice.INDIGO -> if (isDark) {
      AccentPalette(Color(0xFFB8C3FF), Color(0xFF16235E), Color(0xFF2D3B78), Color(0xFFE4E8FF))
    } else {
      AccentPalette(Color(0xFF42539F), Color(0xFFFFFFFF), Color(0xFFE0E4FA), Color(0xFF141B3E))
    }
    AccentChoice.VIOLET -> if (isDark) {
      AccentPalette(Color(0xFFD5B9FA), Color(0xFF381E5E), Color(0xFF4E3378), Color(0xFFEFE6FD))
    } else {
      AccentPalette(Color(0xFF6B4C9A), Color(0xFFFFFFFF), Color(0xFFEDE2FC), Color(0xFF271742))
    }
    AccentChoice.AMBER -> if (isDark) {
      AccentPalette(Color(0xFFF5BF74), Color(0xFF452604), Color(0xFF62390A), Color(0xFFFCE8CC))
    } else {
      AccentPalette(Color(0xFF9A5B13), Color(0xFFFFFFFF), Color(0xFFFCE6C8), Color(0xFF361D03))
    }
  }

private fun baseDarkColorScheme(accent: AccentPalette): ColorScheme = darkColorScheme(
  primary = accent.primary,
  onPrimary = accent.onPrimary,
  primaryContainer = accent.primaryContainer,
  onPrimaryContainer = accent.onPrimaryContainer,
  secondary = accent.primary,
  onSecondary = accent.onPrimary,
  secondaryContainer = accent.primaryContainer,
  onSecondaryContainer = accent.onPrimaryContainer,
  tertiary = accent.primary,
  onTertiary = accent.onPrimary,
  tertiaryContainer = accent.primaryContainer,
  onTertiaryContainer = accent.onPrimaryContainer,
  background = backgroundDark,
  onBackground = onBackgroundDark,
  surface = surfaceDark,
  onSurface = onSurfaceDark,
  surfaceVariant = surfaceVariantDark,
  onSurfaceVariant = onSurfaceVariantDark,
  surfaceContainerLowest = surfaceContainerLowestDark,
  surfaceContainerLow = surfaceContainerLowDark,
  surfaceContainer = surfaceContainerDark,
  surfaceContainerHigh = surfaceContainerHighDark,
  surfaceContainerHighest = surfaceContainerHighestDark,
  outline = outlineDark,
  outlineVariant = outlineVariantDark,
  error = errorDark,
  onError = onErrorDark,
  errorContainer = errorContainerDark,
  onErrorContainer = onErrorContainerDark,
)

private fun baseLightColorScheme(accent: AccentPalette): ColorScheme = lightColorScheme(
  primary = accent.primary,
  onPrimary = accent.onPrimary,
  primaryContainer = accent.primaryContainer,
  onPrimaryContainer = accent.onPrimaryContainer,
  secondary = accent.primary,
  onSecondary = accent.onPrimary,
  secondaryContainer = accent.primaryContainer,
  onSecondaryContainer = accent.onPrimaryContainer,
  tertiary = accent.primary,
  onTertiary = accent.onPrimary,
  tertiaryContainer = accent.primaryContainer,
  onTertiaryContainer = accent.onPrimaryContainer,
  background = backgroundLight,
  onBackground = onBackgroundLight,
  surface = surfaceLight,
  onSurface = onSurfaceLight,
  surfaceVariant = surfaceVariantLight,
  onSurfaceVariant = onSurfaceVariantLight,
  surfaceContainerLowest = surfaceContainerLowestLight,
  surfaceContainerLow = surfaceContainerLowLight,
  surfaceContainer = surfaceContainerLight,
  surfaceContainerHigh = surfaceContainerHighLight,
  surfaceContainerHighest = surfaceContainerHighestLight,
  outline = outlineLight,
  outlineVariant = outlineVariantLight,
  error = errorLight,
  onError = onErrorLight,
  errorContainer = errorContainerLight,
  onErrorContainer = onErrorContainerLight,
)

fun isEffectiveDark(themeMode: ThemeMode, systemDark: Boolean): Boolean = when (themeMode) {
  ThemeMode.SYSTEM -> systemDark
  ThemeMode.LIGHT -> false
  ThemeMode.DARK, ThemeMode.AMOLED -> true
}

fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
  background = Color.Black,
  surface = Color(0xFF0C0F14),
  surfaceVariant = Color(0xFF171E29),
  surfaceContainerLowest = Color.Black,
  surfaceContainerLow = Color(0xFF11161E),
  surfaceContainer = Color(0xFF151B24),
  surfaceContainerHigh = Color(0xFF1C2430),
  surfaceContainerHighest = Color(0xFF242E3D),
  outlineVariant = Color(0xFF2A3544),
)

fun resolveNexusColorScheme(
  context: Context?,
  themeMode: ThemeMode,
  accentChoice: AccentChoice,
  systemDark: Boolean,
): ColorScheme {
  val isDark = isEffectiveDark(themeMode, systemDark)
  val baseScheme = if (
    accentChoice == AccentChoice.WALLPAPER &&
    context != null &&
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  ) {
    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  } else {
    val accent = accentPaletteFor(accentChoice, isDark)
    if (isDark) baseDarkColorScheme(accent) else baseLightColorScheme(accent)
  }
  return if (themeMode == ThemeMode.AMOLED) baseScheme.withAmoledSurfaces() else baseScheme
}

@Composable
fun NexusTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  accentChoice: AccentChoice = AccentChoice.SLATE_BLUE,
  systemDark: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme = resolveNexusColorScheme(
    context = context,
    themeMode = themeMode,
    accentChoice = accentChoice,
    systemDark = systemDark,
  )

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}

object NexusFrostedStyle {
  val TonalElevation = 0.dp
  val ShadowElevation = 0.dp

  fun surfaceColor(colorScheme: ColorScheme): Color {
    val isLight = colorScheme.background.luminance() > 0.5f
    val tintWeight = if (isLight) 0.42f else 0.24f
    return lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, tintWeight)
  }

  fun toolbarColor(colorScheme: ColorScheme): Color {
    val isLight = colorScheme.background.luminance() > 0.5f
    val tintWeight = if (isLight) 0.30f else 0.18f
    return lerp(colorScheme.background, colorScheme.primaryContainer, tintWeight)
  }

  fun borderColor(colorScheme: ColorScheme): Color =
    colorScheme.primary.copy(alpha = 0.24f)

  fun borderStroke(colorScheme: ColorScheme): BorderStroke =
    BorderStroke(1.dp, borderColor(colorScheme))

  @Composable
  fun cardColors(colorScheme: ColorScheme = MaterialTheme.colorScheme): CardColors =
    CardDefaults.cardColors(
      containerColor = surfaceColor(colorScheme),
      contentColor = colorScheme.onSurface,
    )

  @Composable
  fun cardElevation(): CardElevation =
    CardDefaults.cardElevation(defaultElevation = ShadowElevation)

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun topAppBarColors(colorScheme: ColorScheme = MaterialTheme.colorScheme): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
      containerColor = toolbarColor(colorScheme),
      scrolledContainerColor = toolbarColor(colorScheme),
      titleContentColor = colorScheme.onSurface,
      navigationIconContentColor = colorScheme.onSurface,
      actionIconContentColor = colorScheme.onSurfaceVariant,
    )

  @Composable
  fun textFieldColors(colorScheme: ColorScheme = MaterialTheme.colorScheme): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedContainerColor = surfaceColor(colorScheme),
      unfocusedContainerColor = surfaceColor(colorScheme),
      disabledContainerColor = surfaceColor(colorScheme).copy(alpha = 0.6f),
      focusedBorderColor = colorScheme.primary,
      unfocusedBorderColor = borderColor(colorScheme),
      focusedTextColor = colorScheme.onSurface,
      unfocusedTextColor = colorScheme.onSurface,
    )
}
