package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.StructureDetector
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.StructureDetectionResult
import com.c0x12c.pageindex.api.model.TocEntry

/**
 * Markdown structure detector.
 *
 * Detects four pattern types:
 * - ATX headers: `# Title` through `###### Title`
 * - Setext headers: `Title\n===` (level 1) and `Title\n---` (level 2)
 * - Bold-line headers: `**Title**` alone on a line (level 1)
 * - Numbered outlines: `1. Title`, `1.1 Title`, `1.1.1 Title` (level = segment count)
 */
class MarkdownRegexDetector : StructureDetector {

  override suspend fun detect(
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): StructureDetectionResult? {
    val entries = mutableListOf<TocEntry>()

    for (page in pages) {
      val lines = page.text.lines()
      var inCodeBlock = false
      var i = 0

      while (i < lines.size) {
        val line = lines[i]

        if (line.trimStart().startsWith(CODE_FENCE)) {
          inCodeBlock = !inCodeBlock
          i++
          continue
        }
        if (inCodeBlock) {
          i++
          continue
        }

        // ATX headers (highest priority)
        val atxMatch = ATX_HEADER.find(line)
        if (atxMatch != null) {
          val title = atxMatch.groupValues[2].trim()
          if (title.isNotBlank()) {
            entries.add(TocEntry(
              title = title,
              pageNumber = null,
              physicalIndex = page.pageNumber,
              level = atxMatch.groupValues[1].length
            ))
          }
          i++
          continue
        }

        // Setext headers (check if next line is underline)
        if (i + 1 < lines.size && line.isNotBlank()) {
          val nextLine = lines[i + 1]
          val setextLevel = when {
            SETEXT_H1.matches(nextLine) -> SETEXT_LEVEL_1
            SETEXT_H2.matches(nextLine) && !isPrecededByBlank(lines, i) -> SETEXT_LEVEL_2
            else -> null
          }
          if (setextLevel != null) {
            val title = line.trim()
            if (title.isNotBlank()) {
              entries.add(TocEntry(
                title = title,
                pageNumber = null,
                physicalIndex = page.pageNumber,
                level = setextLevel
              ))
              i += 2 // skip underline
              continue
            }
          }
        }

        // Numbered outlines: 1. Title, 1.1 Title, 1.1.1 Title
        val numberedMatch = NUMBERED_OUTLINE.find(line)
        if (numberedMatch != null) {
          val numbering = numberedMatch.groupValues[1]
          val title = numberedMatch.groupValues[2].trim()
          if (title.isNotBlank() && !PURE_NUMERIC.matches(title)) {
            val level = numbering.split(DOT_SEPARATOR).filter { it.isNotEmpty() }.size
            entries.add(TocEntry(
              title = title,
              pageNumber = null,
              physicalIndex = page.pageNumber,
              level = level.coerceAtLeast(1)
            ))
            i++
            continue
          }
        }

        // Bold-line headers: **Title** alone on a line
        val boldMatch = BOLD_LINE.find(line)
        if (boldMatch != null) {
          val title = boldMatch.groupValues[1].trim()
          if (title.isNotBlank()) {
            entries.add(TocEntry(
              title = title,
              pageNumber = null,
              physicalIndex = page.pageNumber,
              level = BOLD_DEFAULT_LEVEL
            ))
          }
        }

        i++
      }
    }

    if (entries.size < config.minHeaderCount) return null

    return StructureDetectionResult(
      entries = entries,
      method = IndexingMethod.HEADER_BASED
    )
  }

  private fun isPrecededByBlank(lines: List<String>, index: Int): Boolean {
    if (index == 0) return true
    return lines[index - 1].isBlank()
  }

  companion object {
    private const val CODE_FENCE = "```"
    private const val DOT_SEPARATOR = "."
    private const val SETEXT_LEVEL_1 = 1
    private const val SETEXT_LEVEL_2 = 2
    private const val BOLD_DEFAULT_LEVEL = 1

    internal val ATX_HEADER = Regex("^(#{1,6})\\s+(.+)$")
    internal val SETEXT_H1 = Regex("^={3,}\\s*$")
    internal val SETEXT_H2 = Regex("^-{3,}\\s*$")
    internal val BOLD_LINE = Regex("^\\s*\\*\\*(.+?)\\*\\*\\s*$")
    internal val NUMBERED_OUTLINE = Regex("^\\s*(\\d+(?:\\.\\d+)*)\\.?\\s+(.+)$")
    private val PURE_NUMERIC = Regex("^[\\d.\\s]+$")
  }
}
