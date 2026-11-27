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

  @Test
  fun matchesQuery_multiWordAcrossFieldsAndExcludesPhoneFromMixedTextFallback() {
    val priya = alice.copy(
      id = "p1",
      name = "Priya Nair",
      phone = "+91 98111 20240",
      service = "IPS",
      cadre = "Assam",
      serviceBatch = "2025",
    )
    val partial = alice.copy(
      id = "p2",
      name = "Priya Verma",
      phone = "+91 98111 20241",
      service = "IPS",
      cadre = "Kerala",
      serviceBatch = "2024",
    )

    // Matches all three words across fields regardless of order
    assertTrue(DirectorySearch.matchesQuery(priya, "Priya IPS Assam"))
    assertTrue(DirectorySearch.matchesQuery(priya, "Assam priya ips"))
    // Missing one word is excluded
    assertFalse(DirectorySearch.matchesQuery(partial, "Priya IPS Assam"))

    // Mixed text query "IAS 2024" must not match phone digits "20240"
    val iasWith2024InPhone = alice.copy(
      id = "p3",
      phone = "+91 98111 20240",
      service = "IAS",
      serviceBatch = "2026",
    )
    assertFalse(DirectorySearch.matchesQuery(iasWith2024InPhone, "IAS 2024"))
    // Formatted phone search still works
    assertTrue(DirectorySearch.matchesQuery(iasWith2024InPhone, "+91 98111-20240"))
  }

  @Test
  fun filterAndSort_searchesCategoriesReusableGroupsPastPostingsAndIntroducers() {
    val katiharOfficer = alice.copy(
      id = "k1",
      name = "Rohan Das",
      category = null,
      customCategory = "Heritage Walk Group",
      postings = listOf(
        PostingEntry(id = "old", organisation = "District Collectorate", designation = "SDM", location = "Katihar", isCurrent = false),
        PostingEntry(id = "cur", organisation = "Secretariat", designation = "Joint Secretary", location = "Patna", isCurrent = true),
      ),
      introductions = listOf(
        IntroductionRecord(id = "i1", introducerProfileId = "removed-id", introducerName = "Ravi Menon"),
      ),
    )
    val typedIntro = bob.copy(
      id = "k2",
      name = "Neha Gupta",
      introductions = listOf(
        IntroductionRecord(id = "i2", introducerProfileId = null, introducerName = "Ravi Kumar"),
      ),
    )
    val group = ContactGroup(
      id = "g1",
      name = "District Visit Team",
      memberIds = listOf("k1"),
      createdAtEpochMs = 10L,
    )
    val profiles = listOf(katiharOfficer, typedIntro)

    // Built-in & custom categories
    assertEquals(listOf("k2"), DirectorySearch.filterAndSort(profiles, "Faculty", null, null, false, false, allGroups = listOf(group)).map { it.id })
    assertEquals(listOf("k1"), DirectorySearch.filterAndSort(profiles, "Heritage Walk", null, null, false, false, allGroups = listOf(group)).map { it.id })

    // Reusable group name & rename
    assertEquals(listOf("k1"), DirectorySearch.filterAndSort(profiles, "District Visit Team", null, null, false, false, allGroups = listOf(group)).map { it.id })
    val renamed = group.copy(name = "Field Survey Squad")
    assertTrue(DirectorySearch.filterAndSort(profiles, "District Visit Team", null, null, false, false, allGroups = listOf(renamed)).isEmpty())
    assertEquals(listOf("k1"), DirectorySearch.filterAndSort(profiles, "Field Survey Squad", null, null, false, false, allGroups = listOf(renamed)).map { it.id })

    // Past posting location
    assertEquals(listOf("k1"), DirectorySearch.filterAndSort(profiles, "Katihar", null, null, false, false, allGroups = listOf(group)).map { it.id })

    // Linked (even with removed profile) and typed introducer names
    assertEquals(listOf("k1", "k2"), DirectorySearch.filterAndSort(profiles, "Ravi", null, null, false, false, allGroups = listOf(group)).map { it.id })

    // Respects active filters
    assertEquals(listOf("k1"), DirectorySearch.filterAndSort(profiles, "Ravi", null, null, true, false, allGroups = listOf(group)).map { it.id })
  }
}
