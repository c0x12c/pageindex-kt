package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.ParsedPage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class FlatPagesFallbackTest {

  private val detector = FlatPagesFallback()

  @Test
  fun `detect - always returns a result`() = runTest {
    val pages = listOf(ParsedPage(pageNumber = 1, text = "Some text."))

    val result = detector.detect(pages, testConfig())

    assertNotNull(result)
  }

  @Test
  fun `detect - groups pages into nodes based on maxNodeTextLength`() = runTest {
    val config = testConfig(maxNodeTextLength = 20)
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "1234567890"),
      ParsedPage(pageNumber = 2, text = "1234567890"),
      ParsedPage(pageNumber = 3, text = "1234567890"),
      ParsedPage(pageNumber = 4, text = "1234567890")
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals(2, result?.entries?.size)
  }

  @Test
  fun `detect - titles are Pages X-Y`() = runTest {
    val config = testConfig(maxNodeTextLength = 20)
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "1234567890"),
      ParsedPage(pageNumber = 2, text = "1234567890"),
      ParsedPage(pageNumber = 3, text = "1234567890")
    )

    val result = detector.detect(pages, config)

    assertNotNull(result)
    assertEquals("Pages 1-2", result?.entries?.get(0)?.title)
    assertEquals("Page 3", result?.entries?.get(1)?.title)
  }

  @Test
  fun `detect - method is FLAT_PAGES`() = runTest {
    val pages = listOf(ParsedPage(pageNumber = 1, text = "Text."))

    val result = detector.detect(pages, testConfig())

    assertEquals(IndexingMethod.FLAT_PAGES, result?.method)
  }

  @Test
  fun `detect - single page returns single entry`() = runTest {
    val pages = listOf(ParsedPage(pageNumber = 5, text = "Single page content."))

    val result = detector.detect(pages, testConfig())

    assertEquals(1, result?.entries?.size)
    assertEquals("Page 5", result?.entries?.get(0)?.title)
    assertEquals(5, result?.entries?.get(0)?.physicalIndex)
  }
}
