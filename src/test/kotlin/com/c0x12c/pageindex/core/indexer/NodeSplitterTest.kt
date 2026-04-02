package com.c0x12c.pageindex.core.indexer

import com.c0x12c.pageindex.api.model.TreeNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NodeSplitterTest {

  private fun leaf(
    title: String,
    text: String?,
    startPage: Int = 1,
    endPage: Int = 10
  ) = TreeNode(
    nodeId = "0001",
    title = title,
    level = 1,
    startPage = startPage,
    endPage = endPage,
    text = text
  )

  @Test
  fun `splitOversizedNodes - keeps nodes under limit unchanged`() {
    val nodes = listOf(leaf("Ch1", "short text"))

    val result = NodeSplitter.splitOversizedNodes(nodes, maxTextLength = 100)

    assertEquals(1, result.size)
    assertEquals("short text", result[0].text)
    assertTrue(result[0].children.isEmpty())
  }

  @Test
  fun `splitOversizedNodes - splits node exceeding limit into parts`() {
    val nodes = listOf(leaf("Ch1", "a".repeat(100), startPage = 1, endPage = 10))

    val result = NodeSplitter.splitOversizedNodes(nodes, maxTextLength = 40)

    assertEquals(1, result.size)
    assertNull(result[0].text)
    assertEquals(3, result[0].children.size)
    assertEquals("Ch1 (Part 1)", result[0].children[0].title)
    assertEquals("Ch1 (Part 2)", result[0].children[1].title)
    assertEquals("Ch1 (Part 3)", result[0].children[2].title)
  }

  @Test
  fun `splitOversizedNodes - split parts have correct page ranges`() {
    val nodes = listOf(leaf("Ch1", "a".repeat(80), startPage = 1, endPage = 10))

    val result = NodeSplitter.splitOversizedNodes(nodes, maxTextLength = 40)

    val children = result[0].children
    assertEquals(1, children[0].startPage)
    assertEquals(10, children.last().endPage)
  }

  @Test
  fun `splitOversizedNodes - preserves existing children`() {
    val child = TreeNode(
      nodeId = "0002",
      title = "Existing Child",
      level = 2,
      startPage = 1,
      endPage = 5,
      text = "child text"
    )
    val parent = leaf("Ch1", "a".repeat(100), startPage = 1, endPage = 10)
      .copy(children = listOf(child))

    val result = NodeSplitter.splitOversizedNodes(listOf(parent), maxTextLength = 40)

    val allChildren = result[0].children
    assertTrue(allChildren.any { it.title == "Existing Child" })
    assertTrue(allChildren.any { it.title.startsWith("Ch1 (Part") })
  }

  @Test
  fun `splitOversizedNodes - handles empty text`() {
    val nodes = listOf(leaf("Ch1", ""))

    val result = NodeSplitter.splitOversizedNodes(nodes, maxTextLength = 40)

    assertEquals(1, result.size)
    assertTrue(result[0].children.isEmpty())
  }

  @Test
  fun `splitOversizedNodes - recursively splits children`() {
    val child = TreeNode(
      nodeId = "0002",
      title = "Big Child",
      level = 2,
      startPage = 1,
      endPage = 5,
      text = "b".repeat(100)
    )
    val parent = leaf("Ch1", "short").copy(children = listOf(child))

    val result = NodeSplitter.splitOversizedNodes(listOf(parent), maxTextLength = 40)

    val processedChild = result[0].children[0]
    assertNull(processedChild.text)
    assertTrue(processedChild.children.isNotEmpty())
  }

  @Test
  fun `splitOversizedNodes - splits at paragraph boundary when available`() {
    val text = "First paragraph content.\n\nSecond paragraph content.\n\nThird paragraph content here."
    val nodes = listOf(leaf("Ch1", text, startPage = 1, endPage = 3))

    val result = NodeSplitter.splitOversizedNodes(nodes, maxTextLength = 50)

    val children = result[0].children
    assertTrue(children.size >= 2)
    assertTrue(children[0].text?.endsWith("\n\n") == true || !children[0].text.orEmpty().contains("\n\nSecond"))
  }
}
