package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.ParsedPage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegexTocDetectorTest {

  private val detector = RegexTocDetector()
  private val config = testConfig()

  @Test
  fun `detect - finds TOC with dot leaders and page numbers`() = runTest {
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          Table of Contents
          Introduction . . . . . . . . 1
          Background . . . . . . . . . 5
          Methods . . . . . . . . . . . 12
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(IndexingMethod.TOC_WITH_PAGES, result?.method)
    assertEquals(3, result?.entries?.size)
    assertEquals("Introduction", result?.entries?.get(0)?.title)
    assertEquals(1, result?.entries?.get(0)?.pageNumber)
  }

  @Test
  fun `detect - finds TOC with Table of Contents header`() = runTest {
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          TABLE OF CONTENTS
          Chapter 1 . . . . . 3
          Chapter 2 . . . . . 8
          Chapter 3 . . . . . 15
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
  }

  @Test
  fun `detect - extracts correct page numbers from entries`() = runTest {
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          Table of Contents
          First Section . . . . . 10
          Second Section . . . . . 25
          Third Section . . . . . 42
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(10, result?.entries?.get(0)?.pageNumber)
    assertEquals(25, result?.entries?.get(1)?.pageNumber)
    assertEquals(42, result?.entries?.get(2)?.pageNumber)
  }

  @Test
  fun `detect - parses hierarchy from numbering`() = runTest {
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          Table of Contents
          1 Introduction . . . . . 1
          1.1 Background . . . . . 3
          1.2 Scope . . . . . . . . 5
          2 Methods . . . . . . . . 10
          2.1 Data Collection . . . 12
          2.1.1 Sampling . . . . . 14
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(1, result?.entries?.find { it.title.contains("Introduction") }?.level)
    assertEquals(2, result?.entries?.find { it.title.contains("Background") }?.level)
    assertEquals(3, result?.entries?.find { it.title.contains("Sampling") }?.level)
  }

  @Test
  fun `detect - returns null when no TOC patterns found`() = runTest {
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "Just some regular text about the environment."),
      ParsedPage(pageNumber = 2, text = "More regular content with no structure.")
    )

    val result = detector.detect(pages, config)

    assertNull(result)
  }

  @Test
  fun `detect - finds TOC on any page including end of document`() = runTest {
    // TOC at the end (page 10 of 10) — still detected since we scan all pages
    val pages = (1..9).map { ParsedPage(pageNumber = it, text = "Content page $it.") } +
      listOf(
        ParsedPage(
          pageNumber = 10,
          text = """
            Table of Contents
            Chapter 1 . . . . . 1
            Chapter 2 . . . . . 5
            Chapter 3 . . . . . 8
          """.trimIndent()
        )
      )

    val result = detector.detect(pages, config)

    // TOC found despite being at the end
    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
    // TOC in second half (page 10 > 10/2=5) → offset = 1 - minLogical = 0
    // Physical indices equal logical page numbers
    assertEquals(1, result?.entries?.get(0)?.physicalIndex)
    assertEquals(5, result?.entries?.get(1)?.physicalIndex)
    assertEquals(8, result?.entries?.get(2)?.physicalIndex)
    // lastContentPage excludes the TOC page so the final section doesn't capture TOC text
    assertEquals(9, result?.lastContentPage)
  }

  @Test
  fun `detect - handles TOC spanning multiple pages`() = runTest {
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          Table of Contents
          Chapter 1 . . . . . 5
          Chapter 2 . . . . . 10
          Chapter 3 . . . . . 15
        """.trimIndent()
      ),
      ParsedPage(
        pageNumber = 2,
        text = """
          Chapter 4 . . . . . 20
          Chapter 5 . . . . . 25
          Chapter 6 . . . . . 30
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(6, result?.entries?.size)
  }

  @Test
  fun `detect - sets lastContentPage to totalPages for beginning-of-doc TOC`() = runTest {
    // TOC on page 1, content on pages 2-10 → lastContentPage = 10 (= totalPages)
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          Table of Contents
          Chapter 1 . . . . . 2
          Chapter 2 . . . . . 6
        """.trimIndent()
      )
    ) + (2..10).map { ParsedPage(pageNumber = it, text = "Content page $it.") }

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(10, result?.lastContentPage)
  }

  @Test
  fun `detect - sets rawTocText for verification`() = runTest {
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          Table of Contents
          Section A . . . . . 1
          Section B . . . . . 5
          Section C . . . . . 10
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result?.rawTocText)
    assertEquals(pages[0].text, result?.rawTocText)
  }

  // --- GridProjector output compatibility tests ---

  @Test
  fun `detect - matches GridProjector space-leader output (dots lost to column gaps)`() = runTest {
    // GridProjector renders TOC as: "Introduction    10" when dots become column spacing
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          TABLE OF CONTENTS
          Introduction    1
          Site Description    5
          Environmental Setting    12
          Findings and Conclusions    20
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(IndexingMethod.TOC_WITH_PAGES, result?.method)
    assertEquals(4, result?.entries?.size)
    assertEquals("Introduction", result?.entries?.get(0)?.title)
    assertEquals(1, result?.entries?.get(0)?.pageNumber)
  }

  @Test
  fun `detect - handles # prefixed TOC title from GridProjector`() = runTest {
    // GridProjector adds "# " prefix when TOC title has larger font
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          # TABLE OF CONTENTS
          Introduction . . . . . 1
          Background . . . . . . 5
          Methods . . . . . . . . 12
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
  }

  @Test
  fun `detect - handles ## prefixed contents header from GridProjector`() = runTest {
    // GridProjector may add "## " if the header is a medium-size font
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          ## Table of Contents
          Site History . . . . . 3
          Regulatory Review . . . 8
          Site Reconnaissance . . 15
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
  }

  @Test
  fun `detect - handles TOC entries with # prefix stripped before matching`() = runTest {
    // GridProjector might prefix TOC entry text if those lines have slightly larger font
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          TABLE OF CONTENTS
          ## Executive Summary    2
          ## Site Description    5
          ## Historical Research    10
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
    assertEquals("Executive Summary", result?.entries?.get(0)?.title)
    assertEquals(2, result?.entries?.get(0)?.pageNumber)
  }

  @Test
  fun `detect - matches numbered sections with space-leader format`() = runTest {
    // Numbered ESA sections with GridProjector column spacing
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          TABLE OF CONTENTS
          1.0 Introduction    1
          2.0 Site Description    5
          3.0 User Provided Information    8
          4.0 Records Review    12
          5.0 Site Reconnaissance    20
          6.0 Findings, Opinions and Conclusions    25
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(6, result?.entries?.size)
    assertEquals(1, result?.entries?.get(0)?.level)
    assertEquals(5, result?.entries?.get(1)?.pageNumber)
  }

  @Test
  fun `detect - matches real ESA report TOC with trailing dots after page number`() = runTest {
    // Actual parser output from Final_Phase_I_ESA.pdf — format: "Title    PageNum............"
    // Provide 30 pages so the heuristic correctly identifies page 3 as beginning-of-doc TOC
    // (3 * 2 = 6 < 30 → beginning-of-doc path → offset = 3+1-1 = 3)
    val tocPage = ParsedPage(
      pageNumber = 3,
      text = """
TABLE OF CONTENTS

EXECUTIVE SUMMARY    1............................................................................................................................
1.0 INTRODUCTION    3.................................................................................................................................
1.1 Purpose    3...................................................................................................................................................................................
1.2 Scope-of-Services    3..................................................................................................................................................................
2.0 SUBJECT PROPERTY DESCRIPTION    6...............................................................................................
2.1 Ownership and Location    6.....................................................................................................................................................
2.6.1 Topography    7......................................................................................................................................................................
3.0 USER PROVIDED INFORMATION    8....................................................................................................
4.0 RECORDS REVIEW    9..............................................................................................................................
5.0 SUBJECT PROPERTY RECONNAISSANCE    17...................................................................................
8.0 FINDINGS AND OPINIONS    26.............................................................................................................
9.0 RECOMMENDATIONS    27......................................................................................................................
      """.trimIndent()
    )
    val pages = (1..30).map { n -> if (n == 3) tocPage else ParsedPage(pageNumber = n, text = "Page $n content.") }

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(IndexingMethod.TOC_WITH_PAGES, result?.method)
    val titles = result?.entries?.map { it.title }
    assert(titles?.any { it.contains("INTRODUCTION") } == true)
    assert(titles?.any { it.contains("SUBJECT PROPERTY DESCRIPTION") } == true)
    assert(titles?.any { it.contains("RECORDS REVIEW") } == true)
    // Logical page numbers (from TOC text) are preserved
    assertEquals(3, result?.entries?.find { it.title.contains("INTRODUCTION") }?.pageNumber)
    assertEquals(6, result?.entries?.find { it.title.contains("Ownership") }?.pageNumber)
    assertEquals(3, result?.entries?.find { it.title.contains("Topography") }?.level)
    // Physical indices offset from last TOC page (3) + 1 - min logical page (1) = offset 3
    // Executive Summary logical 1 → physical 4; Introduction logical 3 → physical 6
    assertEquals(4, result?.entries?.find { it.title.contains("EXECUTIVE SUMMARY") }?.physicalIndex)
    assertEquals(6, result?.entries?.find { it.title.contains("INTRODUCTION") }?.physicalIndex)
    assertEquals(9, result?.entries?.find { it.title.contains("SUBJECT PROPERTY DESCRIPTION") }?.physicalIndex)
  }

  @Test
  fun `detect - does not false-positive on body text with spaces`() = runTest {
    // Regular body text should not trigger space-leader detection
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          The site is located at 123 Main Street    approximately 2 acres in size.
          Groundwater depth is    15 feet below grade.
          No recognized environmental conditions    were identified.
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    // Should return null — no TOC header and fewer than minTocLineCount matches
    assertNull(result)
  }

  @Test
  fun `detect - returns null for cover page form fields mistaken for TOC entries`() = runTest {
    // Cover page of an ESA report rendered by GridProjector.
    // Form fields like "Card    1", "Building Number    1", "0    2", "8    2" match SPACE_LEADER
    // but are NOT a real TOC — they all cluster at pages 1–2 and include pure-numeric titles.
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          ESA Phase I Environmental Site Assessment
          Card    1
          Building Number    1
          Project Number    1
          Report Date    2
          0    2
          8    2
          9    2
        """.trimIndent()
      )
    ) + (2..30).map { ParsedPage(pageNumber = it, text = "Content page $it.") }

    val result = detector.detect(pages, config)

    // Must be rejected: only 2 distinct target pages and pure-numeric titles
    assertNull(result)
  }

  @Test
  fun `detect - dot leader with section inner page suffix like 1-1`() = runTest {
    val pages = listOf(
      ParsedPage(
        pageNumber = 2,
        text = """
          TABLE OF CONTENTS
          1.0   SUMMARY .................................................................... 1-1
          2.0   INTRODUCTION ................................................................ 2-1
          2.1   Purpose .................................................................. 2-1
          5.0   SITE RECONNAISSANCE ....................................................... 5-1
          6.0   INTERVIEWS ................................................................. 6-1
          7.0   FINDINGS ................................................................... 7-1
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(1, result?.entries?.find { it.title.contains("SUMMARY") }?.pageNumber)
    assertEquals(2, result?.entries?.find { it.title.contains("INTRODUCTION") }?.pageNumber)
    assertEquals(7, result?.entries?.find { it.title.contains("FINDINGS") }?.pageNumber)
  }

  @Test
  fun `detect - permissive single space title and page when TABLE OF CONTENTS header present`() = runTest {
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          TABLE OF CONTENTS
          Introduction 1
          Site history 4
          Regulatory review 8
          Site reconnaissance 12
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(4, result?.entries?.size)
    assertEquals("Introduction", result?.entries?.get(0)?.title)
    assertEquals(1, result?.entries?.get(0)?.pageNumber)
  }

  @Test
  fun `detect - allows small page span when many entries and distinct targets`() = runTest {
    val targets = listOf(1, 1, 2, 2, 3, 3)
    val lines = targets.indices.joinToString("\n") { i ->
      "Section ${i + 1} title text here    ${targets[i]}"
    }
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          Contents
          $lines
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertTrue((result?.entries?.size ?: 0) >= 6)
  }

  @Test
  fun `detect - NBSP between title and page normalizes like space leader`() = runTest {
    val nbsp = "\u00A0"
    val pages = listOf(
      ParsedPage(
        pageNumber = 1,
        text = """
          TABLE OF CONTENTS
          Introduction${nbsp}${nbsp}1
          Background${nbsp}${nbsp}5
          Methods${nbsp}${nbsp}12
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
  }

  @Test
  fun `detect - rejects regulatory database table rows`() = runTest {
    // Regulatory database search result rows match SPACE_LEADER superficially:
    //   "NPL    1  0  0  0  0  NR  0" → title="NPL    1  0  0  0  0  NR", page=0
    //   "LUST    2  1  0  0  0  NR  3" → title="LUST    2  1  0  0  0  NR", page=3
    // Rejected because: page=0 OR title contains double spaces (column gap)
    val pages = listOf(
      ParsedPage(
        pageNumber = 15,
        text = """
          DATABASE SEARCH RESULTS
          NPL    1  0  0  0  0  NR  0
          CERCLIS    0  0  0  0  0  NR  0
          RCRA_TSDF    0  0  0  0  0  NR  0
          LUST    2  1  0  0  0  NR  3
          UST    5  3  0  0  0  NR  5
          SPILLS    0  0  0  0  0  NR  0
          BROWNFIELDS    0  0  0  0  0  NR  0
          ERNS    0  0  0  0  0  NR  0
        """.trimIndent()
      )
    )

    val result = detector.detect(pages, config)

    // All entries rejected — page=0 or title has double-space column gaps
    assertNull(result)
  }
}
