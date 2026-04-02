package com.c0x12c.pageindex.core.util

import com.c0x12c.pageindex.api.model.TocEntry

object PageOffsetCalculator {
  fun applyOffset(entries: List<TocEntry>, totalPages: Int): List<TocEntry> {
    val offsets = entries
      .mapNotNull { entry ->
        val page = entry.pageNumber ?: return@mapNotNull null
        val physical = entry.physicalIndex ?: return@mapNotNull null
        physical - page
      }

    if (offsets.isEmpty()) return entries

    val offset = offsets.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0

    return entries.map { entry ->
      if (entry.pageNumber != null && entry.physicalIndex == null) {
        val physical = (entry.pageNumber + offset).coerceIn(1, totalPages)
        entry.copy(physicalIndex = physical)
      } else {
        entry
      }
    }
  }
}
