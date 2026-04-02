package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.ParsedPage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MarkdownRegexDetectorTest {

  private val detector = MarkdownRegexDetector()
  private val config = testConfig()

  // --- ATX headers (ported from MarkdownHeaderDetectorTest) ---

  @Test
  fun `detect - finds ATX headers and maps to levels`() = runTest {
    val pages = listOf(
      ParsedPage(1, "# Introduction\nSome intro text.\n## Background\nBackground details.")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(IndexingMethod.HEADER_BASED, result?.method)
    assertEquals(2, result?.entries?.size)
    assertEquals("Introduction", result?.entries?.get(0)?.title)
    assertEquals(1, result?.entries?.get(0)?.level)
    assertEquals("Background", result?.entries?.get(1)?.title)
    assertEquals(2, result?.entries?.get(1)?.level)
  }

  @Test
  fun `detect - returns null when fewer than minHeaderCount`() = runTest {
    val pages = listOf(
      ParsedPage(1, "# Only One Header\nSome text.")
    )
    assertNull(detector.detect(pages, config))
  }

  @Test
  fun `detect - sets physicalIndex from page number`() = runTest {
    val pages = listOf(
      ParsedPage(3, "# First Header\nText."),
      ParsedPage(7, "# Second Header\nMore text.")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.get(0)?.physicalIndex)
    assertEquals(7, result?.entries?.get(1)?.physicalIndex)
    assertNull(result?.entries?.get(0)?.pageNumber)
  }

  @Test
  fun `detect - handles mixed ATX levels`() = runTest {
    val pages = listOf(
      ParsedPage(1, "# Chapter 1\n## Section 1.1\n### Subsection 1.1.1\n## Section 1.2")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(4, result?.entries?.size)
    assertEquals(1, result?.entries?.get(0)?.level)
    assertEquals(2, result?.entries?.get(1)?.level)
    assertEquals(3, result?.entries?.get(2)?.level)
    assertEquals(2, result?.entries?.get(3)?.level)
  }

  @Test
  fun `detect - ignores headers inside code blocks`() = runTest {
    val pages = listOf(
      ParsedPage(1, "# Real Header\nSome text.\n```\n# This is a comment in code\n## Also not a header\n```\n## Another Real Header")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(2, result?.entries?.size)
    assertEquals("Real Header", result?.entries?.get(0)?.title)
    assertEquals("Another Real Header", result?.entries?.get(1)?.title)
  }

  // --- Setext headers ---

  @Test
  fun `detect - finds setext H1 headers`() = runTest {
    val pages = listOf(
      ParsedPage(1, "Introduction\n============\nSome text.\n\nBackground\n==========\nMore text.")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(2, result?.entries?.size)
    assertEquals("Introduction", result?.entries?.get(0)?.title)
    assertEquals(1, result?.entries?.get(0)?.level)
    assertEquals("Background", result?.entries?.get(1)?.title)
    assertEquals(1, result?.entries?.get(1)?.level)
  }

  @Test
  fun `detect - finds setext H2 headers`() = runTest {
    val pages = listOf(
      ParsedPage(1, "Main Title\n==========\nIntro.\nSubsection A\n------------\nContent A.\nSubsection B\n------------\nContent B.")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
    assertEquals("Main Title", result?.entries?.get(0)?.title)
    assertEquals(1, result?.entries?.get(0)?.level)
    assertEquals("Subsection A", result?.entries?.get(1)?.title)
    assertEquals(2, result?.entries?.get(1)?.level)
  }

  @Test
  fun `detect - setext dash preceded by blank line is not a header`() = runTest {
    val pages = listOf(
      ParsedPage(1, "# Real Header\n\n---\n\nSome text after horizontal rule.\n\n# Another Header")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(2, result?.entries?.size)
    assertEquals("Real Header", result?.entries?.get(0)?.title)
    assertEquals("Another Header", result?.entries?.get(1)?.title)
  }

  // --- Bold-line headers ---

  @Test
  fun `detect - finds bold-line headers`() = runTest {
    val pages = listOf(
      ParsedPage(1, "**Executive Summary**\nWe had a great year.\n\n**Financial Results**\nRevenue grew 25%.")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(2, result?.entries?.size)
    assertEquals("Executive Summary", result?.entries?.get(0)?.title)
    assertEquals(1, result?.entries?.get(0)?.level)
    assertEquals("Financial Results", result?.entries?.get(1)?.title)
  }

  @Test
  fun `detect - does not match inline bold`() = runTest {
    val pages = listOf(
      ParsedPage(1, "# Real Header\nSome text with **bold word** in a sentence.\n## Another Header")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(2, result?.entries?.size)
    assertEquals("Real Header", result?.entries?.get(0)?.title)
    assertEquals("Another Header", result?.entries?.get(1)?.title)
  }

  // --- Numbered outlines ---

  @Test
  fun `detect - finds numbered outlines with levels`() = runTest {
    val pages = listOf(
      ParsedPage(1, "1. Introduction\nSome text.\n1.1 Background\nMore.\n1.1.1 Details\nEven more.\n2. Conclusion\nDone.")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(4, result?.entries?.size)
    assertEquals("Introduction", result?.entries?.get(0)?.title)
    assertEquals(1, result?.entries?.get(0)?.level)
    assertEquals("Background", result?.entries?.get(1)?.title)
    assertEquals(2, result?.entries?.get(1)?.level)
    assertEquals("Details", result?.entries?.get(2)?.title)
    assertEquals(3, result?.entries?.get(2)?.level)
    assertEquals("Conclusion", result?.entries?.get(3)?.title)
    assertEquals(1, result?.entries?.get(3)?.level)
  }

  // --- Mixed patterns ---

  @Test
  fun `detect - mixed ATX and bold-line across pages`() = runTest {
    val pages = listOf(
      ParsedPage(1, "# Chapter 1\nText on page 1."),
      ParsedPage(2, "**Section 1.1**\nText on page 2."),
      ParsedPage(3, "## Section 1.2\nText on page 3.")
    )
    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
    assertEquals(1, result?.entries?.get(0)?.physicalIndex)
    assertEquals(2, result?.entries?.get(1)?.physicalIndex)
    assertEquals(3, result?.entries?.get(2)?.physicalIndex)
  }
}
