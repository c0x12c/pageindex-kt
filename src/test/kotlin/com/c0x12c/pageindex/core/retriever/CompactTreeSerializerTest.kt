package com.c0x12c.pageindex.core.retriever

import com.c0x12c.pageindex.api.model.DocumentTree
import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.TreeMetadata
import com.c0x12c.pageindex.api.model.TreeNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class CompactTreeSerializerTest {

  private fun node(
    title: String,
    text: String?,
    children: List<TreeNode> = emptyList()
  ) = TreeNode(
    nodeId = "0001",
    title = title,
    level = 1,
    startPage = 1,
    endPage = 5,
    text = text,
    children = children
  )

  private fun tree(nodes: List<TreeNode>) = DocumentTree(
    documentId = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    rootNodes = nodes,
    metadata = TreeMetadata(
      totalNodes = nodes.size,
      totalPages = 10,
      maxDepth = 1,
      indexingModel = "test",
      indexingMethod = IndexingMethod.HEADER_BASED
    )
  )

  @Test
  fun `stripText - removes text from leaf nodes`() {
    val nodes = listOf(node("Ch1", "some text"))

    val result = CompactTreeSerializer.stripText(nodes)

    assertNull(result[0].text)
    assertEquals("Ch1", result[0].title)
  }

  @Test
  fun `stripText - removes text from nested nodes recursively`() {
    val child = node("Child", "child text")
    val parent = node("Parent", "parent text", children = listOf(child))

    val result = CompactTreeSerializer.stripText(listOf(parent))

    assertNull(result[0].text)
    assertNull(result[0].children[0].text)
  }

  @Test
  fun `stripText - keeps titles summaries nodeIds levels`() {
    val n = node("My Title", "text").copy(
      summary = "A summary",
      nodeId = "0042",
      level = 3
    )

    val result = CompactTreeSerializer.stripText(listOf(n))

    assertEquals("My Title", result[0].title)
    assertEquals("A summary", result[0].summary)
    assertEquals("0042", result[0].nodeId)
    assertEquals(3, result[0].level)
  }

  @Test
  fun `toCompactJson - produces valid JSON`() {
    val t = tree(listOf(node("Ch1", "text")))

    val json = CompactTreeSerializer.toCompactJson(t)

    assertFalse(json.isEmpty())
    assert(json.startsWith("{"))
    assert(json.endsWith("}"))
  }

  @Test
  fun `toCompactJson - JSON contains no text fields with content`() {
    val t = tree(listOf(node("Ch1", "secret text that should be stripped")))

    val json = CompactTreeSerializer.toCompactJson(t)

    assertFalse(json.contains("secret text"))
  }
}
