package com.nexus.prm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CsvRosterParserTest {

  @Test
  fun parse_handlesQuotedCommasPhoneAsTextAndRowErrors() {
    val csv = """
      name,phone,email,service,cadre,service_batch,fc_batch,training_group
      "Nair, Priya",+91 98765-43210,priya@example.com,IAS,Kerala,2026,101st FC,"Alpha, Group 1"
      ,9999999999,missing@example.com,IPS,UP,2026,101st FC,Beta
      Rohan Verma,0135-2221000,,,,,,
    """.trimIndent()

    val result = CsvRosterParser.parse(csv, nowEpochMs = 1000L)
    assertNull(result.fatalError)
    assertEquals(2, result.validRows.size)
    assertEquals(1, result.errors.size)

    val first = result.validRows[0].profile
    assertEquals("Nair, Priya", first.name)
    assertEquals("+91 98765-43210", first.phone)
    assertEquals("Alpha, Group 1", first.trainingGroup)

    val third = result.validRows[1].profile
    assertEquals("Rohan Verma", third.name)
    assertEquals("0135-2221000", third.phone)

    assertEquals(3, result.errors.first().lineNumber)
  }

  @Test
  fun parse_missingNameHeaderReturnsFatalError() {
    val csv = "phone,email\n12345,a@b.com"
    val result = CsvRosterParser.parse(csv)
    assertNotNull(result.fatalError)
  }
}
