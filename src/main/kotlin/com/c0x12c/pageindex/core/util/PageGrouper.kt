package com.c0x12c.pageindex.core.util

import com.c0x12c.pageindex.api.model.ParsedPage

object PageGrouper {
  fun group(pages: List<ParsedPage>, maxTokens: Int, overlap: Int): List<List<ParsedPage>> {
    if (pages.isEmpty()) return emptyList()

    val groups = mutableListOf<List<ParsedPage>>()
    var startIdx = 0

    while (startIdx < pages.size) {
      val group = mutableListOf<ParsedPage>()
      var tokenCount = 0
      var endIdx = startIdx

      while (endIdx < pages.size) {
        val pageTokens = TokenCounter.estimate(pages[endIdx].text)
        if (tokenCount + pageTokens > maxTokens && group.isNotEmpty()) break
        group.add(pages[endIdx])
        tokenCount += pageTokens
        endIdx++
      }

      groups.add(group)
      if (endIdx >= pages.size) break
      startIdx = (endIdx - overlap).coerceAtLeast(startIdx + 1)
    }

    return groups
  }
}
