package com.nexus.prm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerCardVCardTest {

  @Test
  fun buildPreview_includesOnlySelectedNonBlankFields() {
    val card = OwnerCard(
      name = "Ayush Kumar",
      phone = "+91 98765 43210",
      email = "ayush@example.com",
      service = "IAS",
      cadre = "Uttarakhand",
      shareName = true,
      sharePhone = false,
      shareEmail = true,
      shareService = true,
      shareCadre = false,
    )

    val preview = OwnerCardVCard.buildPreview(card)
    assertEquals(listOf("Name" to "Ayush Kumar", "Email" to "ayush@example.com", "Service" to "IAS"), preview.lines)
    assertTrue(preview.vCardPayload.contains("FN:Ayush Kumar"))
    assertTrue(preview.vCardPayload.contains("EMAIL:ayush@example.com"))
    assertTrue(preview.vCardPayload.contains("Service: IAS"))
    assertFalse(preview.vCardPayload.contains("98765"))
    assertFalse(preview.vCardPayload.contains("Uttarakhand"))
  }
}
