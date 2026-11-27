package com.nexus.prm.data

object CsvExporter {
  private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')

  fun export(
    profiles: List<PersonProfile>,
    includeMeetingNotes: Boolean = false,
  ): String {
    val sb = StringBuilder()
    val headers = mutableListOf(
      "name",
      "phone",
      "email",
      "category",
      "organisation",
      "designation",
      "service",
      "cadre",
      "service_batch",
      "fc_batch",
      "training_group",
      "tags",
    )
    if (includeMeetingNotes) {
      headers += "where_we_met"
    }
    sb.append(headers.joinToString(",")).append("\r\n")

    for (raw in profiles) {
      val profile = raw.withSyncedPostings()
      val currentRole = profile.currentPosting()
      val currentOrg = currentRole?.organisation ?: profile.organisation
      val currentDesignation = currentRole?.designation ?: profile.designation

      val cells = mutableListOf(
        profile.name,
        profile.phone,
        profile.email,
        profile.category?.label.orEmpty(),
        currentOrg,
        currentDesignation,
        profile.service,
        profile.cadre,
        profile.serviceBatch,
        profile.foundationCourseBatch,
        profile.trainingGroup,
        profile.tags.joinToString("; "),
      )
      if (includeMeetingNotes) {
        cells += profile.whereWeMet
      }
      sb.append(cells.joinToString(",") { formatCell(it) }).append("\r\n")
    }
    return sb.toString()
  }

  private fun formatCell(raw: String): String {
    val safe = if (raw.isNotEmpty() && raw[0] in FORMULA_PREFIXES) {
      "'$raw"
    } else {
      raw
    }
    val needsQuoting = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuoting) {
      "\"${safe.replace("\"", "\"\"")}\""
    } else {
      safe
    }
  }
}
