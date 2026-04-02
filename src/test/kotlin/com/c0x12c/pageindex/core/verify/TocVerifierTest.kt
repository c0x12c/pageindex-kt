package com.c0x12c.pageindex.core.verify

import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.TocEntry
import com.c0x12c.pageindex.core.detector.FakeLlmClient
import com.c0x12c.pageindex.core.prompt.ResourcePromptProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TocVerifierTest {

  private val promptProvider = ResourcePromptProvider()

  private val yesResponse = """{"thinking": "The title appears in the text", "answer": "yes"}"""
  private val noResponse = """{"thinking": "The title does not appear in the text", "answer": "no"}"""

  private fun pages(count: Int = 5) = (1..count).map {
    ParsedPage(pageNumber = it, text = "Page $it content with section title here")
  }

  private fun entries(count: Int) = (1..count).map { i ->
    TocEntry(title = "Section $i", pageNumber = i, physicalIndex = i, level = 1)
  }

  @Test
  fun `verify - returns accuracy 1_0 when all items correct`() = runTest {
    val fake = FakeLlmClient().onTag("verification", yesResponse)
    val verifier = TocVerifier(fake, promptProvider)

    val result = verifier.verify(entries(3), pages(5), sampleSize = 0)

    assertEquals(1.0, result.accuracy)
    assertTrue(result.incorrectItems.isEmpty())
  }

  @Test
  fun `verify - returns accuracy 0_0 when all items wrong`() = runTest {
    val fake = FakeLlmClient().onTag("verification", noResponse)
    val verifier = TocVerifier(fake, promptProvider)

    val result = verifier.verify(entries(3), pages(5), sampleSize = 0)

    assertEquals(0.0, result.accuracy)
    assertEquals(3, result.incorrectItems.size)
  }

  @Test
  fun `verify - returns accuracy 0_5 when half correct`() = runTest {
    var callCount = 0
    val fake = object : LlmClient {
      override suspend fun chat(
        messages: List<LlmMessage>,
        tags: List<String>
      ): String {
        callCount++
        return if (callCount % 2 == 1) yesResponse else noResponse
      }
    }
    val verifier = TocVerifier(fake, promptProvider)

    val result = verifier.verify(entries(4), pages(5), sampleSize = 0)

    assertEquals(0.5, result.accuracy)
    assertEquals(2, result.incorrectItems.size)
  }

  @Test
  fun `verify - verifies all items when sampleSize is 0`() = runTest {
    val fake = FakeLlmClient().onTag("verification", yesResponse)
    val verifier = TocVerifier(fake, promptProvider)
    val allEntries = entries(4)

    verifier.verify(allEntries, pages(5), sampleSize = 0)

    assertEquals(4, fake.callCount("verification"))
  }

  @Test
  fun `verify - verifies subset when sampleSize is greater than 0`() = runTest {
    val fake = FakeLlmClient().onTag("verification", yesResponse)
    val verifier = TocVerifier(fake, promptProvider)
    val allEntries = entries(10)

    verifier.verify(allEntries, pages(10), sampleSize = 3)

    assertEquals(3, fake.callCount("verification"))
  }

  @Test
  fun `verify - identifies incorrect items with reasons`() = runTest {
    val fake = FakeLlmClient().onTag("verification", noResponse)
    val verifier = TocVerifier(fake, promptProvider)

    val result = verifier.verify(entries(2), pages(5), sampleSize = 0)

    assertEquals(2, result.incorrectItems.size)
    result.incorrectItems.forEach { item ->
      assertTrue(item.reason.isNotEmpty())
    }
  }

  @Test
  fun `verify - skips entries without physicalIndex`() = runTest {
    val fake = FakeLlmClient().onTag("verification", yesResponse)
    val verifier = TocVerifier(fake, promptProvider)
    val mixedEntries = listOf(
      TocEntry(title = "Section 1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Section 2", pageNumber = null, physicalIndex = null, level = 1),
      TocEntry(title = "Section 3", pageNumber = 3, physicalIndex = 3, level = 1)
    )

    verifier.verify(mixedEntries, pages(5), sampleSize = 0)

    assertEquals(2, fake.callCount("verification"))
  }

  @Test
  fun `verify - handles empty entries list`() = runTest {
    val fake = FakeLlmClient().onTag("verification", yesResponse)
    val verifier = TocVerifier(fake, promptProvider)

    val result = verifier.verify(emptyList(), pages(5), sampleSize = 0)

    assertEquals(1.0, result.accuracy)
    assertTrue(result.incorrectItems.isEmpty())
    assertEquals(0, fake.callCount("verification"))
  }
}
