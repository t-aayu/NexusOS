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

  @Test
  fun clampCallerCardPosition_keepsCardInsideUsableBoundsWithMargins() {
    // Usable: 0..1080 x 60..2200, card: 960 x 300, margins: 60 x 24
    // minX = 60, maxX = 1080 - 60 - 960 = 60
    // minY = 84, maxY = 2200 - 24 - 300 = 1876
    assertEquals(
      60 to 84,
      clampCallerCardPosition(
        x = -100,
        y = 10,
        cardWidth = 960,
        cardHeight = 300,
        usableLeft = 0,
        usableTop = 60,
        usableRight = 1080,
        usableBottom = 2200,
        horizontalMarginPx = 60,
        verticalMarginPx = 24,
      ),
    )
    assertEquals(
      60 to 1876,
      clampCallerCardPosition(
        x = 500,
        y = 2500,
        cardWidth = 960,
        cardHeight = 300,
        usableLeft = 0,
        usableTop = 60,
        usableRight = 1080,
        usableBottom = 2200,
        horizontalMarginPx = 60,
        verticalMarginPx = 24,
      ),
    )
  }

  @Test
  fun selectFittingTags_showsOverflowChipWhenRowFull() {
    // 4 tags of width 100 each, gap 10, maxRowWidth 300 -> 2 tags (200) + 2 gaps (20) + "+2" (60) = 280 <= 300
    assertEquals(
      2 to 2,
      selectFittingTags(
        tagWidthsPx = listOf(100, 100, 100, 100),
        overflowChipWidthProvider = { 60 },
        gapPx = 10,
        maxRowWidthPx = 300,
      ),
    )
    // All fit without overflow
    assertEquals(
      2 to 0,
      selectFittingTags(
        tagWidthsPx = listOf(100, 100),
        overflowChipWidthProvider = { 60 },
        gapPx = 10,
        maxRowWidthPx = 300,
      ),
    )
    // Empty
    assertEquals(
      0 to 0,
      selectFittingTags(
        tagWidthsPx = emptyList(),
        overflowChipWidthProvider = { 60 },
        gapPx = 10,
        maxRowWidthPx = 300,
      ),
    )
  }
}
