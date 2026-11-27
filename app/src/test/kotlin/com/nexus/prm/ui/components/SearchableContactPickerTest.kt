package com.nexus.prm.ui.components

import com.nexus.prm.data.PersonProfile
import com.nexus.prm.ui.people.personCardSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchableContactPickerTest {
  private val profiles = listOf(
    PersonProfile(
      id = "p1",
      name = "Rajesh Sharma",
      service = "IAS",
      cadre = "AGMUT",
      organisation = "Ministry of Finance",
    ),
    PersonProfile(
      id = "p2",
      name = "Rajesh Sharma",
      service = "IPS",
      cadre = "UP",
      organisation = "State Police HQ",
    ),
    PersonProfile(
      id = "p3",
      name = "Ananya Verma",
      organisation = "NITI Aayog",
    ),
  )

  @Test
  fun filtersPartialNameCaseInsensitivelyAndRestoresOnClear() {
    val filtered = filterContactPickerProfiles(profiles, "raj")
    assertEquals(listOf("p1", "p2"), filtered.map { it.id })

    val upper = filterContactPickerProfiles(profiles, "VERMA")
    assertEquals(listOf("p3"), upper.map { it.id })

    val restored = filterContactPickerProfiles(profiles, "")
    assertEquals(3, restored.size)
  }

  @Test
  fun subtitleDistinguishesSameNameContacts() {
    val firstSub = personCardSubtitle(profiles[0])
    val secondSub = personCardSubtitle(profiles[1])
    assertTrue(firstSub.contains("IAS"))
    assertTrue(firstSub.contains("AGMUT"))
    assertTrue(secondSub.contains("IPS"))
    assertTrue(secondSub.contains("UP"))
    assertEquals("NITI Aayog", personCardSubtitle(profiles[2]))
  }
}
