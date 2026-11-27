package com.nexus.prm.callerid

import com.nexus.prm.data.IntroductionRecord
import com.nexus.prm.data.PersonProfile
import com.nexus.prm.data.PostingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CallerIdMatcherTest {

  private val officer = PersonProfile(
    id = "1",
    name = "Ananya Roy",
    phone = "+91 98765 43210",
    service = "IAS",
    serviceBatch = "2025",
    cadre = "Bihar",
    designation = "Assistant Collector",
    organisation = "District Collectorate",
    whereWeMet = "Secret mess meeting",
    otherDetails = "Private note",
    tags = listOf("ConfidentialTag"),
    introductions = listOf(IntroductionRecord(id = "i1", introducerName = "Private Mentor")),
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 1L,
  )

  @Test
  fun findUnambiguousMatch_handlesIndianFormatsUnknownAndDuplicates() {
    val list = listOf(
      officer,
      PersonProfile(id = "2", name = "Other", phone = "9123456789", createdAtEpochMs = 2L, updatedAtEpochMs = 2L),
    )

    assertEquals("1", CallerIdMatcher.findUnambiguousMatch("+91 98765 43210", list)?.id)
    assertEquals("1", CallerIdMatcher.findUnambiguousMatch("9876543210", list)?.id)
    assertEquals("1", CallerIdMatcher.findUnambiguousMatch("098765 43210", list)?.id)
    assertEquals("2", CallerIdMatcher.findUnambiguousMatch("+91 91234 56789", list)?.id)

    assertNull(CallerIdMatcher.findUnambiguousMatch(null, list))
    assertNull(CallerIdMatcher.findUnambiguousMatch("", list))
    assertNull(CallerIdMatcher.findUnambiguousMatch("Private", list))
    assertNull(CallerIdMatcher.findUnambiguousMatch("9999999999", list))

    // Ambiguous duplicate match must be skipped
    val withDuplicate = list + officer.copy(id = "3", name = "Duplicate Contact", phone = "9876543210")
    assertNull(CallerIdMatcher.findUnambiguousMatch("+91 98765 43210", withDuplicate))
  }

  @Test
  fun formatCallerSubtitle_followsThreeDetailOrderAndFallbacks() {
    assertEquals(
      "IAS \u00B7 2025 batch \u00B7 Bihar cadre",
      CallerIdMatcher.formatCallerSubtitle(officer),
    )

    val noCadre = officer.copy(cadre = "")
    assertEquals(
      "IAS \u00B7 2025 batch \u00B7 Assistant Collector",
      CallerIdMatcher.formatCallerSubtitle(noCadre),
    )

    val noBatchOrCadre = officer.copy(
      serviceBatch = "",
      cadre = "",
      designation = "Joint Secretary",
      organisation = "Ministry of Finance",
    )
    assertEquals(
      "IAS \u00B7 Joint Secretary \u00B7 Ministry of Finance",
      CallerIdMatcher.formatCallerSubtitle(noBatchOrCadre),
    )

    val faculty = officer.copy(
      service = "",
      serviceBatch = "",
      cadre = "",
      designation = "Professor",
      organisation = "LBSNAA",
      postings = listOf(PostingEntry(id = "p1", organisation = "LBSNAA", designation = "Professor", isCurrent = true)),
    )
    assertEquals(
      "Professor \u00B7 LBSNAA",
      CallerIdMatcher.formatCallerSubtitle(faculty),
    )

    val nameOnly = PersonProfile(id = "n1", name = "Name Only", createdAtEpochMs = 1L, updatedAtEpochMs = 1L)
    assertEquals("", CallerIdMatcher.formatCallerSubtitle(nameOnly))

    val subtitle = CallerIdMatcher.formatCallerSubtitle(officer)
    assertFalse(subtitle.contains("Secret mess meeting"))
    assertFalse(subtitle.contains("Private note"))
    assertFalse(subtitle.contains("ConfidentialTag"))
    assertFalse(subtitle.contains("Private Mentor"))
  }

  @Test
  fun resolveStatus_reportsOffSetupRequiredAndActive() {
    assertEquals(CallerIdStatus.OFF, CallerIdPreferences.resolveStatus(enabled = false, roleHeld = false))
    assertEquals(CallerIdStatus.OFF, CallerIdPreferences.resolveStatus(enabled = false, roleHeld = true))
    assertEquals(CallerIdStatus.SETUP_REQUIRED, CallerIdPreferences.resolveStatus(enabled = true, roleHeld = false))
    assertEquals(CallerIdStatus.ACTIVE, CallerIdPreferences.resolveStatus(enabled = true, roleHeld = true))
  }
}
