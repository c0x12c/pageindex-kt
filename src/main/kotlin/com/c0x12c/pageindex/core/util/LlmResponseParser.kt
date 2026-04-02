package com.c0x12c.pageindex.core.util

object LlmResponseParser {
  private val PHYSICAL_INDEX_PATTERN = Regex("<physical_index_(\\d+)>")

  fun parsePhysicalIndex(value: String?): Int? {
    if (value == null) return null
    val match = PHYSICAL_INDEX_PATTERN.find(value)
    return match?.groupValues?.get(1)?.toIntOrNull()
  }

  fun detectLevel(structure: String?): Int {
    if (structure == null) return 1
    return structure.count { it == '.' } + 1
  }
}
