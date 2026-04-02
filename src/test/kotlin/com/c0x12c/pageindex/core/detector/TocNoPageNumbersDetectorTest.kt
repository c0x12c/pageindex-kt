package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.core.prompt.ResourcePromptProvider
import com.c0x12c.pageindex.core.util.LlmResponseParser
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TocNoPageNumbersDetectorTest {

  private val promptProvider = ResourcePromptProvider()

  private val tocDetectYes = """{"thinking": "this is a toc", "toc_detected": "yes"}"""
  private val tocDetectNo = """{"thinking": "not a toc", "toc_detected": "no"}"""
  private val extractedToc = "1. Introduction\n2. Methods\n3. Results"
  private val completenessYes = """{"thinking": "looks complete", "completed": "yes"}"""
  private val completenessNo = """{"thinking": "looks incomplete", "completed": "no"}"""
  private val transformedToc = """
    {
      "table_of_contents": [
        {"structure": "1", "title": "Introduction", "page": null},
        {"structure": "2", "title": "Methods", "page": null},
        {"structure": "3", "title": "Results", "page": null}
      ]
    }
  """.trimIndent()
  private val noPageNumbers = """{"thinking": "no pages", "page_index_given_in_toc": "no"}"""
  private val hasPageNumbers = """{"thinking": "has pages", "page_index_given_in_toc": "yes"}"""
  private val matchedStructure = """
    [
      {"structure": "1", "title": "Introduction", "start": "yes", "physical_index": "<physical_index_2>"},
      {"structure": "2", "title": "Methods", "start": "yes", "physical_index": "<physical_index_5>"},
      {"structure": "3", "title": "Results", "start": "no", "physical_index": null}
    ]
  """.trimIndent()

  private fun pages(count: Int = 5) = (1..count).map {
    ParsedPage(pageNumber = it, text = "Page $it content with some text to fill space")
  }

  private fun buildFakeClient() = FakeLlmClient()
    .onTag("toc-detection", tocDetectYes)
    .onTag("toc-extraction", extractedToc)
    .onTag("toc-completeness", completenessYes)
    .onTag("toc-transform", transformedToc)
    .onTag("page-index-detection", noPageNumbers)
    .onTag("toc-page-matching", matchedStructure)

  @Test
  fun `detect - returns null when no TOC pages found`() = runTest {
    val fake = FakeLlmClient().onTag("toc-detection", tocDetectNo)
    val detector = TocNoPageNumbersDetector(fake, promptProvider)

    val result = detector.detect(pages(), testConfig())

    assertNull(result)
  }

  @Test
  fun `detect - finds consecutive TOC pages via LLM`() = runTest {
    val fake = buildFakeClient()
    val detector = TocNoPageNumbersDetector(fake, promptProvider)

    val result = detector.detect(pages(3), testConfig())

    assertNotNull(result)
  }

  @Test
  fun `detect - scans all pages when no TOC found`() = runTest {
    val fake = FakeLlmClient(defaultResponse = tocDetectNo)
      .onTag("toc-detection", tocDetectNo)

    val detector = TocNoPageNumbersDetector(fake, promptProvider)

    val result = detector.detect(pages(5), testConfig())

    assertNull(result)
    assertEquals(5, fake.callCount("toc-detection"))
  }

  @Test
  fun `detect - returns null when TOC has page numbers (defers to regex)`() = runTest {
    val fake = buildFakeClient()
      .onTag("page-index-detection", hasPageNumbers)
    val detector = TocNoPageNumbersDetector(fake, promptProvider)

    val result = detector.detect(pages(3), testConfig())

    assertNull(result)
  }

  @Test
  fun `detect - matches titles to physical pages across groups`() = runTest {
    val fake = buildFakeClient()
    val detector = TocNoPageNumbersDetector(fake, promptProvider)

    val result = detector.detect(pages(3), testConfig())

    assertNotNull(result)
    val entries = result?.entries ?: emptyList()
    assertEquals("Introduction", entries[0].title)
    assertEquals(2, entries[0].physicalIndex)
    assertEquals("Methods", entries[1].title)
    assertEquals(5, entries[1].physicalIndex)
  }

  @Test
  fun `detect - returns TOC_WITHOUT_PAGES method`() = runTest {
    val fake = buildFakeClient()
    val detector = TocNoPageNumbersDetector(fake, promptProvider)

    val result = detector.detect(pages(3), testConfig())

    assertEquals(IndexingMethod.TOC_WITHOUT_PAGES, result?.method)
  }

  @Test
  fun `detect - parses physical_index tags from LLM response`() = runTest {
    val fake = buildFakeClient()
    val detector = TocNoPageNumbersDetector(fake, promptProvider)

    val result = detector.detect(pages(3), testConfig())

    assertNotNull(result)
    assertEquals(2, result?.entries?.get(0)?.physicalIndex)
    assertEquals(5, result?.entries?.get(1)?.physicalIndex)
    assertNull(result?.entries?.get(2)?.physicalIndex)
  }

  @Test
  fun `detect - handles LLM returning unparseable response (returns null)`() = runTest {
    val fake = FakeLlmClient()
      .onTag("toc-detection", tocDetectYes)
      .onTag("toc-extraction", extractedToc)
      .onTag("toc-completeness", completenessYes)
      .onTag("toc-transform", "not valid json at all")
    val detector = TocNoPageNumbersDetector(fake, promptProvider)

    val result = detector.detect(pages(3), testConfig())

    assertNull(result)
  }

  @Test
  fun `detect - retries extraction when incomplete`() = runTest {
    var completenessCallCount = 0
    val fake = object : LlmClient {
      override suspend fun chat(
        messages: List<LlmMessage>,
        tags: List<String>
      ): String {
        val tag = tags.firstOrNull()
        return when (tag) {
          "toc-detection" -> tocDetectYes
          "toc-extraction" -> extractedToc
          "toc-completeness" -> {
            completenessCallCount++
            if (completenessCallCount < 2) completenessNo else completenessYes
          }
          "toc-extraction-continue" -> " more content"
          "toc-transform" -> transformedToc
          "page-index-detection" -> noPageNumbers
          "toc-page-matching" -> matchedStructure
          else -> "{}"
        }
      }
    }
    val detector = TocNoPageNumbersDetector(fake, promptProvider)

    val result = detector.detect(pages(3), testConfig())

    assertNotNull(result)
    assertEquals(2, completenessCallCount)
  }

  @Test
  fun `parsePhysicalIndex - extracts number from tag`() {
    assertEquals(5, LlmResponseParser.parsePhysicalIndex("<physical_index_5>"))
    assertEquals(12, LlmResponseParser.parsePhysicalIndex("<physical_index_12>"))
    assertEquals(1, LlmResponseParser.parsePhysicalIndex("<physical_index_1>"))
  }

  @Test
  fun `parsePhysicalIndex - returns null for invalid format`() {
    assertNull(LlmResponseParser.parsePhysicalIndex(null))
    assertNull(LlmResponseParser.parsePhysicalIndex(""))
    assertNull(LlmResponseParser.parsePhysicalIndex("physical_index_5"))
    assertNull(LlmResponseParser.parsePhysicalIndex("some random text"))
  }

  @Test
  fun `detectLevel - returns depth from structure string`() {
    assertEquals(1, LlmResponseParser.detectLevel("1"))
    assertEquals(2, LlmResponseParser.detectLevel("1.1"))
    assertEquals(3, LlmResponseParser.detectLevel("1.1.1"))
    assertEquals(4, LlmResponseParser.detectLevel("2.3.1.4"))
  }

  @Test
  fun `detectLevel - returns 1 for null structure`() {
    assertEquals(1, LlmResponseParser.detectLevel(null))
  }
}
