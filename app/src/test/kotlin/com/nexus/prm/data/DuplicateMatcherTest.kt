package com.nexus.prm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateMatcherTest {

  private val existing = listOf(
    PersonProfile(
      id = "p1",
      name = "Aarav Mehta",
      phone = "+91 98765-43210",
      email = "Aarav.Mehta@Example.com",
      createdAtEpochMs = 100L,
      updatedAtEpochMs = 100L,
    ),
  )

  @Test
  fun findMatch_matchesNormalizedPhoneOrTrimmedCaseInsensitiveEmail() {
    val byPhone = DuplicateMatcher.findMatch(
      phone = "91 98765 43210",
      email = "",
      candidates = existing,
    )
    assertNotNull(byPhone)
    assertEquals(DuplicateMatchField.PHONE, byPhone?.matchedField)

    val byEmail = DuplicateMatcher.findMatch(
      phone = "",
      email = "  aarav.mehta@example.com ",
      candidates = existing,
    )
    assertNotNull(byEmail)
    assertEquals(DuplicateMatchField.EMAIL, byEmail?.matchedField)

    val byBoth = DuplicateMatcher.findMatch(
      phone = "+919876543210",
      email = "aarav.mehta@example.com",
      candidates = existing,
    )
    assertNotNull(byBoth)
    assertEquals(DuplicateMatchField.PHONE_AND_EMAIL, byBoth?.matchedField)
  }

  @Test
  fun findMatch_ignoresSharedNameOnlyAndExcludesSelf() {
    assertNull(
      DuplicateMatcher.findMatch(
        phone = "",
        email = "",
        candidates = existing,
      ),
    )

    assertNull(
      DuplicateMatcher.findMatch(
        phone = "+91 98765-43210",
        email = "aarav.mehta@example.com",
        candidates = existing,
        excludeProfileId = "p1",
      ),
    )
  }
}
