package io.pageindex.core

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.pageindex.api.PageIndexException
import io.pageindex.api.model.DocumentTree
import io.pageindex.core.model.NodeSelectionResult
import io.pageindex.api.model.LlmMessage
import io.pageindex.api.model.LlmRole
import io.pageindex.api.model.RetrievedContext
import io.pageindex.api.model.TreeNode

class DefaultNodeRetriever(
  private val structuredChatService: StructuredChatService,
  private val maxNodes: Int
) : NodeRetriever {

  override suspend fun retrieve(
    tree: DocumentTree,
    messages: List<LlmMessage>
  ): Either<PageIndexException, RetrievedContext> {
    val compactJson = CompactTreeSerializer.toCompactJson(tree)

    val systemMessage = LlmMessage(
      LlmRole.SYSTEM,
      "You are a document retrieval system. Given the document index below, " +
        "select the nodes most likely to contain relevant information.\n\n" +
        "Document Index:\n$compactJson\n\n" +
        "Select at most $maxNodes nodes. Prefer specific sections over broad ones."
    )

    val selection = structuredChatService.chat(
      messages = listOf(systemMessage) + messages,
      responseType = NodeSelectionResult::class.java,
      tags = listOf("node-selection", "page-index")
    ).getOrElse { return it.left() }

    val selectedNodes = collectNodesByIds(tree.rootNodes, selection.selectedNodeIds)

    val sectionsText = selectedNodes.joinToString("\n") { node ->
      "--- Section: ${node.title} (Pages ${node.startPage}-${node.endPage}) ---\n${node.text.orEmpty()}"
    }

    return RetrievedContext(
      selectedNodeIds = selectedNodes.map { it.nodeId },
      reasoning = selection.reasoning,
      sectionsText = sectionsText,
      sourcePages = selectedNodes
        .flatMap { (it.startPage..it.endPage).toList() }
        .distinct()
    ).right()
  }

  private fun collectNodesByIds(
    nodes: List<TreeNode>,
    ids: List<String>
  ): List<TreeNode> {
    val result = mutableListOf<TreeNode>()
    for (node in nodes) {
      if (node.nodeId in ids) result.add(node)
      result.addAll(collectNodesByIds(node.children, ids))
    }
    return result
  }
}
