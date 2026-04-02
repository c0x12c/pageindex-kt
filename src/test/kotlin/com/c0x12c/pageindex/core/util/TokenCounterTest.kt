package com.c0x12c.pageindex.core.util

import com.c0x12c.pageindex.api.model.ParsedPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TokenCounterTest {

  @Test
  fun `estimate - returns chars divided by 4`() {
    assertEquals(5, TokenCounter.estimate("twenty chars string!"))
  }

  @Test
  fun `estimate - handles empty string`() {
    assertEquals(0, TokenCounter.estimate(""))
  }

  @Test
  fun `estimate pages - sums all page tokens`() {
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "abcd"), // 1 token
      ParsedPage(pageNumber = 2, text = "abcdefgh") // 2 tokens
    )
    assertEquals(3, TokenCounter.estimate(pages))
  }
}
