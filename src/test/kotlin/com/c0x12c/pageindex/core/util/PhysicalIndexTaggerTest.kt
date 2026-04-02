package com.c0x12c.pageindex.core.util

import com.c0x12c.pageindex.api.model.ParsedPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhysicalIndexTaggerTest {

  @Test
  fun `tag - wraps single page with tags`() {
    val pages = listOf(ParsedPage(pageNumber = 3, text = "hello"))
    val result = PhysicalIndexTagger.tag(pages)

    assertEquals(1, result.size)
    assertEquals("<physical_index_3>hello</physical_index_3>", result[0].text)
  }

  @Test
  fun `tag - wraps multiple pages with correct indices`() {
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "page one"),
      ParsedPage(pageNumber = 2, text = "page two")
    )
    val result = PhysicalIndexTagger.tag(pages)

    assertEquals("<physical_index_1>page one</physical_index_1>", result[0].text)
    assertEquals("<physical_index_2>page two</physical_index_2>", result[1].text)
  }

  @Test
  fun `tag - returns empty list for empty input`() {
    assertEquals(emptyList<ParsedPage>(), PhysicalIndexTagger.tag(emptyList()))
  }

  @Test
  fun `tag - preserves original page numbers`() {
    val pages = listOf(ParsedPage(pageNumber = 42, text = "content"))
    val result = PhysicalIndexTagger.tag(pages)

    assertEquals(42, result[0].pageNumber)
  }
}
