package com.nexus.prm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerCardVCardTest {

  @Test
  fun shareableName_obeysNameSelectionForShareMetadata() {
    val card = OwnerCard(name = " Private Name ", shareName = false)

    assertEquals("", card.shareableName())
    assertFalse(OwnerCardVCard.buildPreview(card).vCardPayload.contains("Private Name"))
    assertEquals("Private Name", card.copy(shareName = true).shareableName())
  }

  @Test
  fun generateQrBitmap_returnsNullForAnOversizedCard() {
    val payload = OwnerCardVCard.buildPreview(OwnerCard(name = "x".repeat(4096))).vCardPayload

    assertNull(OwnerCardVCard.generateQrBitmap(payload))
  }

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

  @Test
  fun buildOwnerShareText_formatsOnlySelectedFields() {
    val card = OwnerCard(
      name = "Ayush Kumar",
      phone = "+91 98765 43210",
      email = "ayush@example.com",
      service = "IAS",
      shareName = true,
      sharePhone = false,
      shareEmail = true,
      shareService = true,
    )

    val text = OwnerCardVCard.buildOwnerShareText(card)
    assertEquals(
      "Name: Ayush Kumar\nEmail: ayush@example.com\nService: IAS",
      text,
    )
  }
}
