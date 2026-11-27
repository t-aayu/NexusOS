package com.nexus.prm.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAndAppearanceTest {

  @Test
  fun themePreferences_defaultsAndLegacyWallpaperMigration() {
    assertEquals(ThemeMode.SYSTEM, ThemePreferences.resolveSavedThemeMode(null))
    assertEquals(ThemeMode.AMOLED, ThemePreferences.resolveSavedThemeMode("AMOLED"))
    assertEquals(
      AccentChoice.SLATE_BLUE,
      ThemePreferences.resolveSavedAccentChoice(null, legacyWallpaperEnabled = false),
    )
    assertEquals(
      AccentChoice.WALLPAPER,
      ThemePreferences.resolveSavedAccentChoice(null, legacyWallpaperEnabled = true),
    )
    assertEquals(
      AccentChoice.TEAL,
      ThemePreferences.resolveSavedAccentChoice("TEAL", legacyWallpaperEnabled = true),
    )
  }

  @Test
  fun isEffectiveDark_respectsSystemAndForcedModes() {
    assertFalse(isEffectiveDark(ThemeMode.SYSTEM, systemDark = false))
    assertTrue(isEffectiveDark(ThemeMode.SYSTEM, systemDark = true))
    assertFalse(isEffectiveDark(ThemeMode.LIGHT, systemDark = true))
    assertTrue(isEffectiveDark(ThemeMode.DARK, systemDark = false))
    assertTrue(isEffectiveDark(ThemeMode.AMOLED, systemDark = false))
  }

  @Test
  fun resolveNexusColorScheme_verifiesPalettesAndAmoledBlackBackgroundAcrossAccents() {
    val lightSlate = resolveNexusColorScheme(null, ThemeMode.LIGHT, AccentChoice.SLATE_BLUE, systemDark = true)
    assertEquals(Color(0xFF3E5F82), lightSlate.primary)
    assertEquals(Color(0xFFFFFFFF), lightSlate.onPrimary)
    assertEquals(Color(0xFFF6F8FB), lightSlate.background)
    assertEquals(Color(0xFFFFFFFF), lightSlate.surface)
    assertEquals(Color(0xFFDCE8F8), lightSlate.primaryContainer)
    assertEquals(Color(0xFF17212E), lightSlate.onBackground)

    val darkSlate = resolveNexusColorScheme(null, ThemeMode.DARK, AccentChoice.SLATE_BLUE, systemDark = false)
    assertEquals(Color(0xFFABC7EB), darkSlate.primary)
    assertEquals(Color(0xFF142B44), darkSlate.onPrimary)
    assertEquals(Color(0xFF10151D), darkSlate.background)
    assertEquals(Color(0xFF1A2431), darkSlate.surface)
    assertEquals(Color(0xFF2A405B), darkSlate.primaryContainer)
    assertEquals(Color(0xFFE8EEF7), darkSlate.onBackground)

    val seenPrimaries = mutableSetOf<Color>()
    for (choice in AccentChoice.entries.filter { it != AccentChoice.WALLPAPER }) {
      val amoled = resolveNexusColorScheme(null, ThemeMode.AMOLED, choice, systemDark = false)
      assertEquals(Color.Black, amoled.background)
      assertEquals(Color.Black, amoled.surfaceContainerLowest)
      assertNotEquals(Color.Black, amoled.surface)
      assertTrue(seenPrimaries.add(amoled.primary))
    }
  }
}
