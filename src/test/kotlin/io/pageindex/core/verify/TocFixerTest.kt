package io.pageindex.core.verify

import io.pageindex.core.model.IncorrectItem
import io.pageindex.api.model.ParsedPage
import io.pageindex.api.model.TocEntry
import io.pageindex.core.detector.FakeLlmClient
import io.pageindex.core.prompt.ResourcePromptProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TocFixerTest {

  private val promptProvider = ResourcePromptProvider()

  private val successResponse = """{"thinking": "Found on page 3", "physical_index": "<physical_index_3>"}"""
  private val failureResponse = """{"thinking": "Cannot determine", "physical_index": "unknown"}"""

  private fun pages(count: Int = 10) = (1..count).map {
    ParsedPage(pageNumber = it, text = "<physical_index_$it>Page $it content</physical_index_$it>")
  }

  @Test
  fun `fix - fixes incorrect item by finding correct page in nearby pages`() = runTest {
    val fake = FakeLlmClient().onTag("fix", successResponse)
    val fixer = TocFixer(fake, promptProvider)
    val entry = TocEntry(title = "Section A", pageNumber = 1, physicalIndex = 2, level = 1)
    val incorrect = IncorrectItem(entry, 2, "Title not found on page")

    val result = fixer.fix(listOf(entry), listOf(incorrect), pages(), maxAttempts = 3, searchRadius = 5)

    assertEquals(1, result.size)
    assertEquals(3, result[0].physicalIndex)
  }

  @Test
  fun `fix - returns original entry when fix fails`() = runTest {
    val fake = FakeLlmClient().onTag("fix", failureResponse)
    val fixer = TocFixer(fake, promptProvider)
    val entry = TocEntry(title = "Section A", pageNumber = 1, physicalIndex = 2, level = 1)
    val incorrect = IncorrectItem(entry, 2, "Title not found on page")

    val result = fixer.fix(listOf(entry), listOf(incorrect), pages(), maxAttempts = 3, searchRadius = 5)

    assertEquals(1, result.size)
    assertEquals(2, result[0].physicalIndex)
  }

  @Test
  fun `fix - respects maxAttempts limit`() = runTest {
    val fake = FakeLlmClient().onTag("fix", successResponse)
    val fixer = TocFixer(fake, promptProvider)
    val incorrectItems = (1..5).map { i ->
      val entry = TocEntry(title = "Section $i", pageNumber = i, physicalIndex = i, level = 1)
      IncorrectItem(entry, i, "Title not found")
    }
    val entries = incorrectItems.map { it.entry }

    fixer.fix(entries, incorrectItems, pages(), maxAttempts = 2, searchRadius = 5)

    assertEquals(2, fake.callCount("fix"))
  }

  @Test
  fun `fix - updates physicalIndex in returned entries`() = runTest {
    val fake = FakeLlmClient().onTag("fix", successResponse)
    val fixer = TocFixer(fake, promptProvider)
    val entry = TocEntry(title = "Section A", pageNumber = 1, physicalIndex = 5, level = 1)
    val incorrect = IncorrectItem(entry, 5, "Title not found")

    val result = fixer.fix(listOf(entry), listOf(incorrect), pages(), maxAttempts = 3, searchRadius = 5)

    assertEquals(3, result[0].physicalIndex)
  }

  @Test
  fun `fix - handles entries not in incorrect list unchanged`() = runTest {
    val fake = FakeLlmClient().onTag("fix", successResponse)
    val fixer = TocFixer(fake, promptProvider)
    val correctEntry = TocEntry(title = "Section A", pageNumber = 1, physicalIndex = 1, level = 1)
    val wrongEntry = TocEntry(title = "Section B", pageNumber = 2, physicalIndex = 2, level = 1)
    val incorrect = IncorrectItem(wrongEntry, 2, "Title not found")

    val result = fixer.fix(listOf(correctEntry, wrongEntry), listOf(incorrect), pages(), maxAttempts = 3, searchRadius = 5)

    assertEquals(2, result.size)
    assertEquals(1, result[0].physicalIndex)
    assertEquals(3, result[1].physicalIndex)
  }
}
