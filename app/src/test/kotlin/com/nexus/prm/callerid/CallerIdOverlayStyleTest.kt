package com.nexus.prm.callerid

import com.nexus.prm.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CallerIdOverlayStyleTest {

  @Test
  fun computeCallerCardWidthPx_applies20dpMarginsAnd420dpCap() {
    val density = 3f
    // Phone width: 360dp (1080px) -> 360 - 40 = 320dp (960px)
    assertEquals(960, computeCallerCardWidthPx(1080, density))

    // Wide screen: 600dp (1800px) -> capped at 420dp (1260px)
    assertEquals(1260, computeCallerCardWidthPx(1800, density))
  }

  @Test
  fun systemCallerCardThemeMode_followsSystemLightOrDark() {
    assertEquals(ThemeMode.LIGHT, systemCallerCardThemeMode(systemDark = false))
    assertEquals(ThemeMode.DARK, systemCallerCardThemeMode(systemDark = true))
  }
}
