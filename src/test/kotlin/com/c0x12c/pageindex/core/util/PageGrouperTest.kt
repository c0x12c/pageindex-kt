package com.c0x12c.pageindex.core.util

import com.c0x12c.pageindex.api.model.ParsedPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageGrouperTest {

  @Test
  fun `group - groups pages under token limit`() {
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "a".repeat(40)), // 10 tokens
      ParsedPage(pageNumber = 2, text = "b".repeat(40)), // 10 tokens
      ParsedPage(pageNumber = 3, text = "c".repeat(40)) // 10 tokens
    )

    val result = PageGrouper.group(pages, maxTokens = 20, overlap = 0)

    assertEquals(2, result.size)
    assertEquals(listOf(1, 2), result[0].map { it.pageNumber })
    assertEquals(listOf(3), result[1].map { it.pageNumber })
  }

  @Test
  fun `group - single page exceeding limit gets its own group`() {
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "a".repeat(200)), // 50 tokens
      ParsedPage(pageNumber = 2, text = "b".repeat(40)) // 10 tokens
    )

    val result = PageGrouper.group(pages, maxTokens = 20, overlap = 0)

    assertEquals(2, result.size)
    assertEquals(listOf(1), result[0].map { it.pageNumber })
    assertEquals(listOf(2), result[1].map { it.pageNumber })
  }

  @Test
  fun `group - applies overlap between groups`() {
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "a".repeat(40)), // 10 tokens
      ParsedPage(pageNumber = 2, text = "b".repeat(40)), // 10 tokens
      ParsedPage(pageNumber = 3, text = "c".repeat(40)), // 10 tokens
      ParsedPage(pageNumber = 4, text = "d".repeat(40)) // 10 tokens
    )

    val result = PageGrouper.group(pages, maxTokens = 20, overlap = 1)

    assertEquals(3, result.size)
    assertEquals(listOf(1, 2), result[0].map { it.pageNumber })
    assertEquals(listOf(2, 3), result[1].map { it.pageNumber })
    assertEquals(listOf(3, 4), result[2].map { it.pageNumber })
  }

  @Test
  fun `group - returns empty list for empty input`() {
    assertEquals(emptyList<List<ParsedPage>>(), PageGrouper.group(emptyList(), maxTokens = 100, overlap = 0))
  }

  @Test
  fun `group - single page returns single group`() {
    val pages = listOf(ParsedPage(pageNumber = 1, text = "hello"))

    val result = PageGrouper.group(pages, maxTokens = 100, overlap = 0)

    assertEquals(1, result.size)
    assertEquals(listOf(1), result[0].map { it.pageNumber })
  }
}
