package com.c0x12c.pageindex.core.util

import com.c0x12c.pageindex.api.model.TocEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PageOffsetCalculatorTest {

  @Test
  fun `applyOffset - calculates correct offset`() {
    val entries = listOf(
      TocEntry(title = "Intro", pageNumber = 1, physicalIndex = 5, level = 1),
      TocEntry(title = "Chapter 1", pageNumber = 10, physicalIndex = null, level = 1)
    )

    val result = PageOffsetCalculator.applyOffset(entries, 20)

    assertEquals(5, result[0].physicalIndex)
    assertEquals(14, result[1].physicalIndex)
  }

  @Test
  fun `applyOffset - uses mode when multiple offsets exist`() {
    val entries = listOf(
      TocEntry(title = "A", pageNumber = 1, physicalIndex = 4, level = 1),
      TocEntry(title = "B", pageNumber = 2, physicalIndex = 5, level = 1),
      TocEntry(title = "C", pageNumber = 3, physicalIndex = 10, level = 1),
      TocEntry(title = "D", pageNumber = 5, physicalIndex = null, level = 1)
    )

    val result = PageOffsetCalculator.applyOffset(entries, 20)

    // Mode offset is 3 (from A: 4-1=3, B: 5-2=3, C: 10-3=7)
    assertEquals(8, result[3].physicalIndex)
  }

  @Test
  fun `applyOffset - returns entries unchanged when no offsets computable`() {
    val entries = listOf(
      TocEntry(title = "A", pageNumber = 1, physicalIndex = null, level = 1),
      TocEntry(title = "B", pageNumber = null, physicalIndex = null, level = 1)
    )

    val result = PageOffsetCalculator.applyOffset(entries, 10)

    assertNull(result[0].physicalIndex)
    assertNull(result[1].physicalIndex)
  }

  @Test
  fun `applyOffset - clamps physical index to totalPages`() {
    val entries = listOf(
      TocEntry(title = "A", pageNumber = 1, physicalIndex = 5, level = 1),
      TocEntry(title = "B", pageNumber = 8, physicalIndex = null, level = 1)
    )

    val result = PageOffsetCalculator.applyOffset(entries, 10)

    // offset = 4, so B would be 8+4=12 but clamped to 10
    assertEquals(10, result[1].physicalIndex)
  }

  @Test
  fun `applyOffset - skips entries that already have physicalIndex`() {
    val entries = listOf(
      TocEntry(title = "A", pageNumber = 1, physicalIndex = 5, level = 1),
      TocEntry(title = "B", pageNumber = 2, physicalIndex = 99, level = 1)
    )

    val result = PageOffsetCalculator.applyOffset(entries, 100)

    assertEquals(99, result[1].physicalIndex)
  }
}
