package com.nexus.prm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorySearchTest {

  private val alice = PersonProfile(
    id = "1",
    name = "Alice Sharma",
    phone = "+91 98765-43210",
    category = PersonCategory.FELLOW_TRAINEE,
    whereWeMet = "LBSNAA Mess breakfast",
    organisation = "Government of India",
    service = "IAS",
    cadre = "Uttarakhand",
    serviceBatch = "2026",
    foundationCourseBatch = "101st FC",
    trainingGroup = "Alpha-1",
    isFavourite = true,
    createdAtEpochMs = 100L,
    updatedAtEpochMs = 100L,
  )

  private val bob = PersonProfile(
    id = "2",
    name = "Dr. Bob Rao",
    phone = "(0135) 222 1000",
    category = PersonCategory.FACULTY,
    whereWeMet = "Polity lecture",
    organisation = "LBSNAA",
    designation = "Professor",
    trainingGroup = "Faculty Wing",
    isFavourite = false,
    createdAtEpochMs = 200L,
    updatedAtEpochMs = 200L,
  )

  @Test
  fun matchesQuery_caseInsensitiveAcrossAllSearchableFields() {
    assertTrue(DirectorySearch.matchesQuery(alice, "alice"))
    assertTrue(DirectorySearch.matchesQuery(alice, "ias"))
    assertTrue(DirectorySearch.matchesQuery(alice, "uttarakhand"))
    assertTrue(DirectorySearch.matchesQuery(alice, "2026"))
    assertTrue(DirectorySearch.matchesQuery(alice, "101st fc"))
    assertTrue(DirectorySearch.matchesQuery(alice, "government"))
    assertTrue(DirectorySearch.matchesQuery(alice, "alpha-1"))
    assertTrue(DirectorySearch.matchesQuery(alice, "mess BREAKFAST"))
    assertFalse(DirectorySearch.matchesQuery(alice, "polity"))
  }

  @Test
  fun matchesQuery_handlesPhoneSpacingAndPunctuation() {
    assertTrue(DirectorySearch.matchesQuery(alice, "9876543210"))
    assertTrue(DirectorySearch.matchesQuery(alice, "98765 432"))
    assertTrue(DirectorySearch.matchesQuery(bob, "01352221000"))
    assertTrue(DirectorySearch.matchesQuery(bob, "222-1000"))
  }

  @Test
  fun filterAndSort_supportsCategoryGroupFavouritesAndRecentlyAdded() {
    val list = listOf(alice, bob)

    assertEquals(
      listOf(bob),
      DirectorySearch.filterAndSort(
        profiles = list,
        query = "",
        selectedCategory = PersonCategory.FACULTY,
        selectedTrainingGroup = null,
        favouritesOnly = false,
        recentlyAddedOnly = false,
      ),
    )

    assertEquals(
      listOf(alice),
      DirectorySearch.filterAndSort(
        profiles = list,
        query = "",
        selectedCategory = null,
        selectedTrainingGroup = "alpha-1",
        favouritesOnly = false,
        recentlyAddedOnly = false,
      ),
    )

    assertEquals(
      listOf(alice),
      DirectorySearch.filterAndSort(
        profiles = list,
        query = "",
        selectedCategory = null,
        selectedTrainingGroup = null,
        favouritesOnly = true,
        recentlyAddedOnly = false,
      ),
    )

    assertEquals(
      listOf(bob, alice),
      DirectorySearch.filterAndSort(
        profiles = list,
        query = "",
        selectedCategory = null,
        selectedTrainingGroup = null,
        favouritesOnly = false,
        recentlyAddedOnly = true,
      ),
    )
  }

  @Test
  fun distinctTrainingGroups_extractsSortedUniqueValues() {
    val charlie = bob.copy(id = "3", trainingGroup = "  alpha-1 ")
    assertEquals(
      listOf("Alpha-1", "Faculty Wing"),
      DirectorySearch.distinctTrainingGroups(listOf(alice, bob, charlie)),
    )
  }

  @Test
  fun matchesQuery_findsByTagAndReusesCanonicalTagCase() {
    val tagged = alice.copy(tags = listOf("Public Health", "Mentor"))
    assertTrue(DirectorySearch.matchesQuery(tagged, "public health"))

    val normalized = DirectorySearch.normalizeTags(
      rawTags = listOf("  public health ", "policy"),
      existingProfiles = listOf(tagged),
    )
    assertEquals(listOf("Public Health", "policy"), normalized)
  }
}
