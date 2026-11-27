package com.nexus.prm.data

data class CsvParsedRow(
  val lineNumber: Int,
  val profile: PersonProfile,
)

data class CsvRowError(
  val lineNumber: Int,
  val rawPreview: String,
  val reason: String,
)

data class CsvParseResult(
  val validRows: List<CsvParsedRow> = emptyList(),
  val errors: List<CsvRowError> = emptyList(),
  val fatalError: String? = null,
)

object CsvRosterParser {
  val EXPECTED_HEADERS = listOf(
    "name",
    "phone",
    "email",
    "service",
    "cadre",
    "service_batch",
    "fc_batch",
    "training_group",
  )

  fun parse(csvText: String, nowEpochMs: Long = System.currentTimeMillis()): CsvParseResult {
    val clean = csvText.removePrefix("\uFEFF")
    if (clean.isBlank()) {
      return CsvParseResult(fatalError = "The selected CSV file is empty.")
    }

    val records = parseRecords(clean)
    if (records.isEmpty()) {
      return CsvParseResult(fatalError = "No rows found in CSV.")
    }

    val headerRecord = records.first()
    if (headerRecord.parseError != null) {
      return CsvParseResult(fatalError = "Header row error: ${headerRecord.parseError}")
    }

    val headerIndex = mutableMapOf<String, Int>()
    headerRecord.fields.forEachIndexed { idx, rawHeader ->
      val key = rawHeader.trim().lowercase()
      if (key.isNotEmpty() && key !in headerIndex) {
        headerIndex[key] = idx
      }
    }

    if ("name" !in headerIndex) {
      return CsvParseResult(
        fatalError = "Missing required 'name' column. Expected headers: ${EXPECTED_HEADERS.joinToString(",")}",
      )
    }

    val validRows = mutableListOf<CsvParsedRow>()
    val errors = mutableListOf<CsvRowError>()

    for (i in 1 until records.size) {
      val record = records[i]
      if (record.parseError != null) {
        errors += CsvRowError(
          lineNumber = record.startLine,
          rawPreview = record.rawLine.take(60),
          reason = record.parseError,
        )
        continue
      }

      if (record.fields.all { it.isBlank() }) continue

      fun field(col: String): String {
        val idx = headerIndex[col] ?: return ""
        return record.fields.getOrNull(idx)?.trim() ?: ""
      }

      val name = field("name")
      if (name.isEmpty()) {
        errors += CsvRowError(
          lineNumber = record.startLine,
          rawPreview = record.rawLine.take(60),
          reason = "Name is required",
        )
        continue
      }

      val profile = PersonProfile(
        id = "csv-${record.startLine}-$nowEpochMs",
        name = name,
        phone = field("phone"),
        email = field("email"),
        service = field("service"),
        cadre = field("cadre"),
        serviceBatch = field("service_batch"),
        foundationCourseBatch = field("fc_batch"),
        trainingGroup = field("training_group"),
        createdAtEpochMs = nowEpochMs + i,
        updatedAtEpochMs = nowEpochMs + i,
      )
      validRows += CsvParsedRow(lineNumber = record.startLine, profile = profile)
    }

    return CsvParseResult(validRows = validRows, errors = errors)
  }

  private data class RawCsvRecord(
    val startLine: Int,
    val rawLine: String,
    val fields: List<String>,
    val parseError: String? = null,
  )

  private fun parseRecords(text: String): List<RawCsvRecord> {
    val records = mutableListOf<RawCsvRecord>()
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    val rawLine = StringBuilder()
    var inQuotes = false
    var lineNumber = 1
    var recordStartLine = 1
    var i = 0

    while (i < text.length) {
      val ch = text[i]
      if (inQuotes) {
        rawLine.append(ch)
        if (ch == '"') {
          if (i + 1 < text.length && text[i + 1] == '"') {
            current.append('"')
            rawLine.append('"')
            i += 2
            continue
          } else {
            inQuotes = false
          }
        } else {
          if (ch == '\n') lineNumber++
          current.append(ch)
        }
      } else {
        when (ch) {
          '"' -> {
            inQuotes = true
            rawLine.append(ch)
          }
          ',' -> {
            fields += current.toString()
            current.clear()
            rawLine.append(ch)
          }
          '\r' -> {
            // Handle CRLF or CR
            if (i + 1 < text.length && text[i + 1] == '\n') {
              i++
            }
            fields += current.toString()
            current.clear()
            records += RawCsvRecord(recordStartLine, rawLine.toString(), fields.toList())
            fields.clear()
            rawLine.clear()
            lineNumber++
            recordStartLine = lineNumber
          }
          '\n' -> {
            fields += current.toString()
            current.clear()
            records += RawCsvRecord(recordStartLine, rawLine.toString(), fields.toList())
            fields.clear()
            rawLine.clear()
            lineNumber++
            recordStartLine = lineNumber
          }
          else -> {
            current.append(ch)
            rawLine.append(ch)
          }
        }
      }
      i++
    }

    if (inQuotes) {
      records += RawCsvRecord(
        startLine = recordStartLine,
        rawLine = rawLine.toString(),
        fields = emptyList(),
        parseError = "Unclosed quoted field",
      )
    } else if (current.isNotEmpty() || fields.isNotEmpty()) {
      fields += current.toString()
      records += RawCsvRecord(recordStartLine, rawLine.toString(), fields.toList())
    }

    return records
  }
}
