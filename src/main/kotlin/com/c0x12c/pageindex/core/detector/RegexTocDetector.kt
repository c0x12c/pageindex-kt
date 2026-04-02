package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.StructureDetector
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.StructureDetectionResult
import com.c0x12c.pageindex.api.model.TocEntry
import java.text.Normalizer

class RegexTocDetector : StructureDetector {

  override suspend fun detect(
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): StructureDetectionResult? {
    val normalizedPages = pages.map { it.copy(text = normalizeForToc(it.text)) }
    val tocPages = normalizedPages.filter { isTocPage(it.text, config) }
    if (tocPages.isEmpty()) return null

    val hadExplicitHeader = tocPages.any { hasContentsHeader(it.text) }
    val rawTocText = tocPages.joinToString("\n") { it.text }
    val logicalEntries = extractEntries(rawTocText, hadExplicitHeader)
    if (logicalEntries.isEmpty()) return null

    val pageNumbers = logicalEntries.mapNotNull { it.pageNumber }
    val pageRange = if (pageNumbers.isEmpty()) 0 else pageNumbers.max() - pageNumbers.min()
    if (pageRange < 3) {
      val distinctPages = pageNumbers.toSet().size
      val allowShortSpan = logicalEntries.size >= 6 && distinctPages >= 3
      if (!allowShortSpan) return null
    }

    val totalPages = normalizedPages.maxOfOrNull { it.pageNumber } ?: 0
    val offset = physicalOffset(tocPages, logicalEntries, totalPages)
    val entries = logicalEntries.map { entry ->
      entry.copy(physicalIndex = entry.pageNumber?.let { (it + offset).coerceIn(1, totalPages) })
    }

    val tocPageNumbers = tocPages.map { it.pageNumber }.toSet()
    val lastContentPage = normalizedPages.filter { it.pageNumber !in tocPageNumbers }.maxOfOrNull { it.pageNumber }

    return StructureDetectionResult(
      entries = entries,
      method = IndexingMethod.TOC_WITH_PAGES,
      rawTocText = rawTocText,
      lastContentPage = lastContentPage
    )
  }

  private fun hasContentsHeader(text: String): Boolean {
    val upper = text.uppercase()
    return upper.contains("TABLE OF CONTENTS") || CONTENTS_HEADER.containsMatchIn(text)
  }

  private fun isTocPage(text: String, config: IndexingConfig): Boolean {
    if (hasContentsHeader(text)) return true
    val lines = text.lines()
    val structuredCount = lines.count { line ->
      val stripped = stripHeaderPrefix(line)
      DOT_LEADER.containsMatchIn(stripped) ||
        DASH_LEADER.containsMatchIn(stripped) ||
        TAB_NUMBER.containsMatchIn(stripped)
    }
    val spaceCount = lines.count { line ->
      SPACE_LEADER.containsMatchIn(stripHeaderPrefix(line))
    }
    return structuredCount >= config.minTocLineCount ||
      (structuredCount >= 1 && spaceCount >= config.minTocLineCount)
  }

  private fun extractEntries(tocText: String, allowPermissiveSingleGapToPage: Boolean): List<TocEntry> {
    val entries = mutableListOf<TocEntry>()
    for (line in tocText.lines()) {
      val stripped = stripHeaderPrefix(line)
      parseStructuredTocLine(stripped)?.let { addParsedEntry(entries, it) }
        ?: run {
          if (!allowPermissiveSingleGapToPage) return@run
          val perm = parsePermissiveTocLine(stripped) ?: return@run
          if (!LETTER_IN_TITLE.containsMatchIn(perm.first)) return@run
          addParsedEntry(entries, perm)
        }
    }
    return entries
  }

  private fun addParsedEntry(entries: MutableList<TocEntry>, titleAndPage: Pair<String, Int>) {
    val title = titleAndPage.first.trim()
    val pageNum = titleAndPage.second
    if (pageNum < 1) return
    if (title.isBlank()) return
    if (PURE_NUMERIC_TITLE.matches(title)) return
    val level = detectLevel(title)
    entries.add(
      TocEntry(
        title = title,
        pageNumber = pageNum,
        physicalIndex = null,
        level = level
      )
    )
  }

  private fun parseStructuredTocLine(stripped: String): Pair<String, Int>? {
    DOT_LEADER.find(stripped)?.let { m ->
      val p = m.groupValues[2].trim().toIntOrNull() ?: return null
      return m.groupValues[1].trimEnd() to p
    }
    DASH_LEADER.find(stripped)?.let { m ->
      val p = m.groupValues[2].trim().toIntOrNull() ?: return null
      return m.groupValues[1].trimEnd() to p
    }
    TAB_NUMBER.find(stripped)?.let { m ->
      val p = m.groupValues[2].trim().toIntOrNull() ?: return null
      return m.groupValues[1].trimEnd() to p
    }
    SPACE_LEADER.find(stripped)?.let { m ->
      val p = m.groupValues[2].trim().toIntOrNull() ?: return null
      return m.groupValues[1].trimEnd() to p
    }
    return null
  }

  /**
   * Last resort for labeled TOC pages: title then a single separator run then page number.
   * Requires a letter in the title to avoid years and numeric noise.
   */
  private fun parsePermissiveTocLine(stripped: String): Pair<String, Int>? {
    val m = PERMISSIVE_TITLE_PAGE.find(stripped) ?: return null
    val pageNum = m.groupValues[2].toIntOrNull() ?: return null
    if (pageNum > 999) return null
    return m.groupValues[1].trimEnd() to pageNum
  }

  private fun physicalOffset(tocPages: List<ParsedPage>, entries: List<TocEntry>, totalPages: Int): Int {
    val lastTocPhysical = tocPages.maxOf { it.pageNumber }
    val minLogical = entries.mapNotNull { it.pageNumber }.minOrNull() ?: 1
    return if (lastTocPhysical * 2 > totalPages) {
      1 - minLogical
    } else {
      lastTocPhysical + 1 - minLogical
    }
  }

  private fun detectLevel(title: String): Int {
    val match = NUMBERING_PATTERN.find(title) ?: return 1
    val numbering = match.groupValues[1]
    val significantParts = numbering.split(".").filter { it != "0" }
    return significantParts.size.coerceAtLeast(1)
  }

  private fun stripHeaderPrefix(line: String): String =
    HEADER_PREFIX.replace(line, "")

  companion object {
    private val LETTER_IN_TITLE = Regex("[A-Za-z]")

    private val HEADER_PREFIX = Regex("^#{1,6}\\s+")

    private val CONTENTS_HEADER = Regex(
      "(?i)^\\s*(?:#{1,6}\\s+)?(table of contents|contents)\\s*$",
      RegexOption.MULTILINE
    )

    private val DOT_LEADER = Regex("^\\s*(.+?)\\s*(?:[.·\\u2026]\\s*){2,}(\\d{1,4})(?:-\\d{1,3})?\\s*$")

    private val DASH_LEADER = Regex("^\\s*(.+?)\\s*-{2,}(\\d{1,4})(?:-\\d{1,3})?\\s*$")

    private val TAB_NUMBER = Regex("^\\s*(.+?)\\t+(\\d{1,4})(?:-\\d{1,3})?\\s*$")

    private val SPACE_LEADER = Regex("^\\s*((?:\\S|\\s(?!\\s))+?)\\s{2,}(\\d{1,4})(?:-\\d{1,3})?[.\\s]*$")

    private val NUMBERING_PATTERN = Regex("^\\s*(\\d+(?:\\.\\d+)*)\\s")

    private val PURE_NUMERIC_TITLE = Regex("^[\\d.\\s]+$")

    private val PERMISSIVE_TITLE_PAGE = Regex("^\\s*(.+?)\\s+([1-9][0-9]{0,2})(?:-\\d{1,3})?\\s*$")
  }

  private fun normalizeForToc(text: String): String {
    val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
    val sb = StringBuilder(nfkc.length)
    for (ch in nfkc) {
      when (ch) {
        '\u00A0', '\u2007', '\u202F', '\u2009', '\u2002', '\u2003', '\uFEFF' -> sb.append(' ')
        else -> sb.append(ch)
      }
    }
    return sb.toString()
  }
}
