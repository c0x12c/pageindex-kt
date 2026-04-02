package com.c0x12c.pageindex.core.indexer

import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.TocEntry
import com.c0x12c.pageindex.api.model.TreeNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TreeBuilderTest {

  @Test
  fun `fromEntries - builds flat list into root nodes`() {
    val entries = listOf(
      TocEntry(title = "Chapter 1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Chapter 2", pageNumber = 3, physicalIndex = 3, level = 1)
    )
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "p1"),
      ParsedPage(pageNumber = 2, text = "p2"),
      ParsedPage(pageNumber = 3, text = "p3")
    )

    val result = TreeBuilder.fromEntries(entries, pages)

    assertEquals(2, result.size)
    assertEquals("Chapter 1", result[0].title)
    assertEquals("Chapter 2", result[1].title)
    assertTrue(result[0].children.isEmpty())
  }

  @Test
  fun `fromEntries - builds 2-level hierarchy`() {
    val entries = listOf(
      TocEntry(title = "Chapter 1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Section 1.1", pageNumber = 2, physicalIndex = 2, level = 2),
      TocEntry(title = "Chapter 2", pageNumber = 3, physicalIndex = 3, level = 1)
    )
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "p1"),
      ParsedPage(pageNumber = 2, text = "p2"),
      ParsedPage(pageNumber = 3, text = "p3")
    )

    val result = TreeBuilder.fromEntries(entries, pages)

    assertEquals(2, result.size)
    assertEquals(1, result[0].children.size)
    assertEquals("Section 1.1", result[0].children[0].title)
  }

  @Test
  fun `fromEntries - builds 3-level hierarchy`() {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Sec1.1", pageNumber = 2, physicalIndex = 2, level = 2),
      TocEntry(title = "Sub1.1.1", pageNumber = 3, physicalIndex = 3, level = 3)
    )
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "p1"),
      ParsedPage(pageNumber = 2, text = "p2"),
      ParsedPage(pageNumber = 3, text = "p3")
    )

    val result = TreeBuilder.fromEntries(entries, pages)

    assertEquals(1, result.size)
    assertEquals(1, result[0].children.size)
    assertEquals(1, result[0].children[0].children.size)
    assertEquals("Sub1.1.1", result[0].children[0].children[0].title)
  }

  @Test
  fun `fromEntries - assigns sequential nodeIds`() {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Sec1.1", pageNumber = 2, physicalIndex = 2, level = 2),
      TocEntry(title = "Ch2", pageNumber = 3, physicalIndex = 3, level = 1)
    )
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "p1"),
      ParsedPage(pageNumber = 2, text = "p2"),
      ParsedPage(pageNumber = 3, text = "p3")
    )

    val result = TreeBuilder.fromEntries(entries, pages)

    assertEquals("0001", result[0].nodeId)
    assertEquals("0002", result[0].children[0].nodeId)
    assertEquals("0003", result[1].nodeId)
  }

  @Test
  fun `fromEntries - assigns text from pages to nodes`() {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Ch2", pageNumber = 3, physicalIndex = 3, level = 1)
    )
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "first"),
      ParsedPage(pageNumber = 2, text = "second"),
      ParsedPage(pageNumber = 3, text = "third")
    )

    val result = TreeBuilder.fromEntries(entries, pages)

    assertEquals("first\nsecond", result[0].text)
    assertEquals("third", result[1].text)
  }

  @Test
  fun `fromEntries - calculates correct startPage and endPage ranges`() {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Ch2", pageNumber = 4, physicalIndex = 4, level = 1)
    )
    val pages = (1..5).map { ParsedPage(pageNumber = it, text = "p$it") }

    val result = TreeBuilder.fromEntries(entries, pages)

    assertEquals(1, result[0].startPage)
    assertEquals(3, result[0].endPage)
    assertEquals(4, result[1].startPage)
    assertEquals(5, result[1].endPage)
  }

  @Test
  fun `fromEntries - parent endPage spans to next sibling, not just immediate next child`() {
    // 2.0 (level 1) page 6, 2.1 (level 2) page 6, 2.2 (level 2) page 6, 3.0 (level 1) page 8.
    // Old logic: 2.0 endPage = 2.1.startPage - 1 = 5, coerced to 6 (wrong — only page 6).
    // New logic: 2.0 endPage = 3.0.startPage - 1 = 7 (correct — spans all of section 2).
    // Entries start at physicalIndex=6, so pages 1-5 produce a "Front Matter" node at result[0].
    val entries = listOf(
      TocEntry(title = "2.0 Description", pageNumber = 6, physicalIndex = 6, level = 1),
      TocEntry(title = "2.1 Ownership", pageNumber = 6, physicalIndex = 6, level = 2),
      TocEntry(title = "2.2 Improvements", pageNumber = 6, physicalIndex = 6, level = 2),
      TocEntry(title = "3.0 Records", pageNumber = 8, physicalIndex = 8, level = 1)
    )
    val pages = (6..8).map { ParsedPage(pageNumber = it, text = "p$it") }

    val result = TreeBuilder.fromEntries(entries, pages)

    // result[0] = Front Matter (pages 1-5, no text since those pages were not provided)
    // result[1] = 2.0 Description, result[2] = 3.0 Records
    assertEquals("Front Matter", result[0].title)
    val section2 = result[1]
    assertEquals(6, section2.startPage)
    assertEquals(7, section2.endPage) // parent spans pages 6-7, not just 6
    assertEquals(6, section2.children[0].startPage)
    assertEquals(6, section2.children[0].endPage) // 2.1 ends before 2.2
    assertEquals(6, section2.children[1].startPage)
    assertEquals(7, section2.children[1].endPage) // 2.2 is last child, ends at 7
  }

  @Test
  fun `fromEntries - prepends front matter node for pages before first section`() {
    // Entries have physicalIndex set (as RegexTocDetector does after offset calculation).
    // Pages 1-2 are cover/TOC pages — they should become a "Front Matter" root node.
    val entries = listOf(
      TocEntry(title = "Executive Summary", pageNumber = 1, physicalIndex = 3, level = 1),
      TocEntry(title = "Introduction", pageNumber = 3, physicalIndex = 5, level = 1)
    )
    val pages = (1..6).map { ParsedPage(pageNumber = it, text = "page$it") }

    val result = TreeBuilder.fromEntries(entries, pages)

    assertEquals(3, result.size) // Front Matter + Executive Summary + Introduction
    assertEquals("Front Matter", result[0].title)
    assertEquals(1, result[0].startPage)
    assertEquals(2, result[0].endPage)
    assertEquals("page1\npage2", result[0].text)
    assertEquals("Executive Summary", result[1].title)
    assertEquals(3, result[1].startPage)
  }

  @Test
  fun `fromEntries - no front matter node when first section starts on page 1`() {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1)
    )
    val pages = listOf(ParsedPage(pageNumber = 1, text = "p1"))

    val result = TreeBuilder.fromEntries(entries, pages)

    assertEquals(1, result.size)
    assertEquals("Ch1", result[0].title)
  }

  @Test
  fun `fromEntries - handles entries without physicalIndex`() {
    val entries = listOf(
      TocEntry(title = "Known", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Unknown", pageNumber = null, physicalIndex = null, level = 1)
    )
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "p1"),
      ParsedPage(pageNumber = 2, text = "p2")
    )

    val result = TreeBuilder.fromEntries(entries, pages)

    assertEquals(2, result.size)
    assertEquals("Known", result[0].title)
  }

  @Test
  fun `fromEntries - caps last section endPage at lastContentPage for end-of-doc TOC`() {
    // Document has 10 pages; TOC is on page 10. lastContentPage=9 should cap the last section.
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Ch2", pageNumber = 6, physicalIndex = 6, level = 1)
    )
    val pages = (1..10).map { ParsedPage(pageNumber = it, text = "p$it") }

    val result = TreeBuilder.fromEntries(entries, pages, lastContentPage = 9)

    // Ch2 should end at page 9, not 10 (the TOC page)
    assertEquals(9, result.last().endPage)
    assertEquals("p6\np7\np8\np9", result.last().text)
  }

  @Test
  fun `fromEntries - handles empty entries list`() {
    val result = TreeBuilder.fromEntries(emptyList(), emptyList())
    assertTrue(result.isEmpty())
  }

  @Test
  fun `fromEntries - root nodes have null parent (wired after build)`() {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Ch2", pageNumber = 3, physicalIndex = 3, level = 1)
    )
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "p1"),
      ParsedPage(pageNumber = 2, text = "p2"),
      ParsedPage(pageNumber = 3, text = "p3")
    )

    val result = TreeBuilder.fromEntries(entries, pages)
    TreeNode.wireParents(result)

    assertNull(result[0].parent)
    assertNull(result[1].parent)
  }

  @Test
  fun `fromEntries - children reference correct parent node after wireParents`() {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Sec1.1", pageNumber = 2, physicalIndex = 2, level = 2),
      TocEntry(title = "Ch2", pageNumber = 3, physicalIndex = 3, level = 1)
    )
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "p1"),
      ParsedPage(pageNumber = 2, text = "p2"),
      ParsedPage(pageNumber = 3, text = "p3")
    )

    val result = TreeBuilder.fromEntries(entries, pages)
    TreeNode.wireParents(result)

    assertEquals("Ch1", result[0].children[0].parent?.title)
    assertEquals("0001", result[0].children[0].parent?.nodeId)
  }

  @Test
  fun `fromEntries - propagates parent through 3-level hierarchy after wireParents`() {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Sec1.1", pageNumber = 2, physicalIndex = 2, level = 2),
      TocEntry(title = "Sub1.1.1", pageNumber = 3, physicalIndex = 3, level = 3)
    )
    val pages = listOf(
      ParsedPage(pageNumber = 1, text = "p1"),
      ParsedPage(pageNumber = 2, text = "p2"),
      ParsedPage(pageNumber = 3, text = "p3")
    )

    val result = TreeBuilder.fromEntries(entries, pages)
    TreeNode.wireParents(result)

    assertNull(result[0].parent)
    assertEquals("Ch1", result[0].children[0].parent?.title)
    assertEquals("Sec1.1", result[0].children[0].children[0].parent?.title)
  }
}
