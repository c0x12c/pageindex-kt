package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.core.prompt.ResourcePromptProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LlmStructureDetectorTest {

  private val promptProvider = ResourcePromptProvider()

  private val initResponse = """
    [
      {"structure": "1", "title": "Introduction", "physical_index": "<physical_index_1>"},
      {"structure": "2", "title": "Methods", "physical_index": "<physical_index_3>"}
    ]
  """.trimIndent()

  private val initResponseSecondGroup = """
    [
      {"structure": "3", "title": "Results", "physical_index": "<physical_index_6>"}
    ]
  """.trimIndent()

  private val mergeResponse = """
    [
      {"structure": "1", "title": "Introduction", "physical_index": "<physical_index_1>"},
      {"structure": "2", "title": "Methods", "physical_index": "<physical_index_3>"},
      {"structure": "3", "title": "Results", "physical_index": "<physical_index_6>"}
    ]
  """.trimIndent()

  private fun pages(count: Int = 3) = (1..count).map {
    ParsedPage(pageNumber = it, text = "Page $it content with enough text to form groups")
  }

  @Test
  fun `detect - parses LLM response into TocEntry list`() = runTest {
    val fake = FakeLlmClient().onTag("structure-detection-init", initResponse)
    val detector = LlmStructureDetector(fake, promptProvider)

    val result = detector.detect(pages(2), testConfig())

    assertNotNull(result)
    assertEquals(2, result?.entries?.size)
    assertEquals("Introduction", result?.entries?.get(0)?.title)
    assertEquals("Methods", result?.entries?.get(1)?.title)
  }

  @Test
  fun `detect - uses init tag for single group`() = runTest {
    val fake = FakeLlmClient().onTag("structure-detection-init", initResponse)
    val detector = LlmStructureDetector(fake, promptProvider)

    detector.detect(pages(2), testConfig())

    assertEquals(1, fake.callCount("structure-detection-init"))
    assertEquals(0, fake.callCount("structure-detection-merge"))
  }

  @Test
  fun `detect - parallel init and merge for multiple groups`() = runTest {
    val fake = FakeLlmClient()
      .queueOnTag("structure-detection-init", initResponse, initResponseSecondGroup)
      .onTag("structure-detection-merge", mergeResponse)

    val config = testConfig().copy(pageGroupMaxTokens = 50, pageGroupOverlap = 0)
    val detector = LlmStructureDetector(fake, promptProvider)

    detector.detect(pages(4), config)

    assertEquals(2, fake.callCount("structure-detection-init"))
    assertEquals(1, fake.callCount("structure-detection-merge"))
    assertEquals(0, fake.callCount("structure-detection-continue"))
  }

  @Test
  fun `detect - merges multi-group document into full entry list`() = runTest {
    val fake = FakeLlmClient()
      .queueOnTag("structure-detection-init", initResponse, initResponseSecondGroup)
      .onTag("structure-detection-merge", mergeResponse)

    val config = testConfig().copy(pageGroupMaxTokens = 70, pageGroupOverlap = 0)
    val detector = LlmStructureDetector(fake, promptProvider)

    val result = detector.detect(pages(6), config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
    assertEquals("Results", result?.entries?.get(2)?.title)
  }

  @Test
  fun `detect - returns null when first group response is unparseable`() = runTest {
    val fake = FakeLlmClient().onTag("structure-detection-init", "not valid json")
    val detector = LlmStructureDetector(fake, promptProvider)

    val result = detector.detect(pages(2), testConfig())

    assertNull(result)
  }

  @Test
  fun `detect - returns null when merge response is unparseable`() = runTest {
    val fake = FakeLlmClient()
      .queueOnTag("structure-detection-init", initResponse, initResponseSecondGroup)
      .onTag("structure-detection-merge", "bad json")

    val config = testConfig().copy(pageGroupMaxTokens = 50, pageGroupOverlap = 0)
    val detector = LlmStructureDetector(fake, promptProvider)

    val result = detector.detect(pages(4), config)

    assertNull(result)
  }

  @Test
  fun `detect - tree merge runs multiple rounds for three groups`() = runTest {
    val third = """[{"structure": "4", "title": "Appendix", "physical_index": "<physical_index_8>"}]"""
    val merge12 = """[
      {"structure": "1", "title": "Introduction", "physical_index": "<physical_index_1>"},
      {"structure": "2", "title": "Methods", "physical_index": "<physical_index_3>"},
      {"structure": "3", "title": "Results", "physical_index": "<physical_index_6>"}
    ]"""
    val mergeFinal = """[
      {"structure": "1", "title": "Introduction", "physical_index": "<physical_index_1>"},
      {"structure": "2", "title": "Methods", "physical_index": "<physical_index_3>"},
      {"structure": "3", "title": "Results", "physical_index": "<physical_index_6>"},
      {"structure": "4", "title": "Appendix", "physical_index": "<physical_index_8>"}
    ]"""

    val fake = FakeLlmClient()
      .queueOnTag("structure-detection-init", initResponse, initResponseSecondGroup, third)
      .queueOnTag("structure-detection-merge", merge12, mergeFinal)

    val config = testConfig().copy(pageGroupMaxTokens = 15, pageGroupOverlap = 0)
    val detector = LlmStructureDetector(fake, promptProvider)

    val result = detector.detect(pages(3), config)

    assertNotNull(result)
    assertEquals(4, result?.entries?.size)
    assertEquals(2, fake.callCount("structure-detection-merge"))
  }

  @Test
  fun `detect - returns LLM_DETECTED method`() = runTest {
    val fake = FakeLlmClient().onTag("structure-detection-init", initResponse)
    val detector = LlmStructureDetector(fake, promptProvider)

    val result = detector.detect(pages(2), testConfig())

    assertEquals(IndexingMethod.LLM_DETECTED, result?.method)
  }

  @Test
  fun `detect - sets physicalIndex from physical_index tags`() = runTest {
    val fake = FakeLlmClient().onTag("structure-detection-init", initResponse)
    val detector = LlmStructureDetector(fake, promptProvider)

    val result = detector.detect(pages(2), testConfig())

    assertNotNull(result)
    assertEquals(1, result?.entries?.get(0)?.physicalIndex)
    assertEquals(3, result?.entries?.get(1)?.physicalIndex)
  }

  @Test
  fun `detect - returns null for empty pages`() = runTest {
    val fake = FakeLlmClient().onTag("structure-detection-init", initResponse)
    val detector = LlmStructureDetector(fake, promptProvider)

    val result = detector.detect(emptyList(), testConfig())

    assertNull(result)
  }
}
