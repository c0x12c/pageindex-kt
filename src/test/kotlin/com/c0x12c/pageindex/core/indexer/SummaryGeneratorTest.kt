package com.c0x12c.pageindex.core.indexer

import com.c0x12c.pageindex.api.PromptProvider
import com.c0x12c.pageindex.api.model.PromptName
import com.c0x12c.pageindex.api.model.TreeNode
import com.c0x12c.pageindex.core.detector.FakeStructuredChatService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SummaryGeneratorTest {

  private val fakeProvider = object : PromptProvider {
    override fun get(name: PromptName): String = when (name) {
      PromptName.SUMMARY -> "Summarize:\n{{sections}}"
      PromptName.ROLLUP_SUMMARY -> "Rollup:\n{{sections}}"
      else -> ""
    }
  }

  private fun leaf(
    nodeId: String,
    title: String = "Title $nodeId",
    text: String? = "Some text for $nodeId"
  ) = TreeNode(
    nodeId = nodeId,
    title = title,
    level = 1,
    startPage = 1,
    endPage = 2,
    text = text
  )

  private fun parent(
    nodeId: String,
    children: List<TreeNode>
  ) = TreeNode(
    nodeId = nodeId,
    title = "Parent $nodeId",
    level = 0,
    startPage = 1,
    endPage = 5,
    children = children
  )

  @Test
  fun `generateSummaries - assigns summaries to leaf nodes`() = runTest {
    val chat = FakeStructuredChatService()
      .onTag("summary", """[{"node_id":"n1","summary":"Summary for n1"},{"node_id":"n2","summary":"Summary for n2"}]""")

    val generator = SummaryGenerator(chat, fakeProvider)
    val nodes = listOf(leaf("n1"), leaf("n2"))

    val result = generator.generateSummaries(nodes, summaryMaxTokens = 500, batchSize = 10)

    assertEquals("Summary for n1", result[0].summary)
    assertEquals("Summary for n2", result[1].summary)
  }

  @Test
  fun `generateSummaries - batches nodes by batchSize`() = runTest {
    val chat = FakeStructuredChatService()
      .onTag("summary", """[{"node_id":"n1","summary":"S1"}]""")

    val generator = SummaryGenerator(chat, fakeProvider)
    val nodes = listOf(leaf("n1"), leaf("n2"), leaf("n3"))

    generator.generateSummaries(nodes, summaryMaxTokens = 500, batchSize = 2)

    assertEquals(2, chat.callCount("summary"))
  }

  @Test
  fun `generateSummaries - handles empty node list`() = runTest {
    val chat = FakeStructuredChatService()
    val generator = SummaryGenerator(chat, fakeProvider)

    val result = generator.generateSummaries(emptyList(), summaryMaxTokens = 500, batchSize = 10)

    assertEquals(emptyList<TreeNode>(), result)
    assertEquals(0, chat.callCount())
  }

  @Test
  fun `generateSummaries - handles response with missing node_ids`() = runTest {
    val chat = FakeStructuredChatService()
      .onTag("summary", """[{"summary":"orphan summary"},{"node_id":"n1","summary":"S1"}]""")

    val generator = SummaryGenerator(chat, fakeProvider)
    val nodes = listOf(leaf("n1"), leaf("n2"))

    val result = generator.generateSummaries(nodes, summaryMaxTokens = 500, batchSize = 10)

    assertEquals("S1", result[0].summary)
    assertNull(result[1].summary)
  }

  @Test
  fun `generateSummaries - preserves node structure while adding summary`() = runTest {
    val chat = FakeStructuredChatService()
      .onTag("summary", """[{"node_id":"child1","summary":"Child summary"}]""")

    val generator = SummaryGenerator(chat, fakeProvider)
    val child = leaf("child1", text = "child text")
    val nodes = listOf(parent("p1", listOf(child)))

    val result = generator.generateSummaries(nodes, summaryMaxTokens = 500, batchSize = 10)

    assertEquals(1, result.size)
    assertEquals("p1", result[0].nodeId)
    assertEquals(1, result[0].children.size)
    assertEquals("Child summary", result[0].children[0].summary)
    assertEquals("child text", result[0].children[0].text)
  }

  @Test
  fun `generateSummaries - leaf gets summary in phase 1, parent gets rollup in phase 2`() = runTest {
    val chat = FakeStructuredChatService()
      .onTag("summary", """[{"node_id":"leaf1","summary":"Leaf summary"},{"node_id":"p1","summary":"Rollup summary"}]""")

    val generator = SummaryGenerator(chat, fakeProvider)
    val nodes = listOf(parent("p1", listOf(leaf("leaf1"))))

    val result = generator.generateSummaries(nodes, summaryMaxTokens = 500, batchSize = 10)

    assertEquals("Rollup summary", result[0].summary)
    assertEquals("Leaf summary", result[0].children[0].summary)
  }
}
