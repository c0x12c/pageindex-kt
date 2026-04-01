package io.pageindex.core

import io.pageindex.api.PromptProvider
import io.pageindex.api.model.LlmMessage
import io.pageindex.api.model.LlmRole
import io.pageindex.api.model.PromptName
import io.pageindex.core.model.SummaryEntry
import io.pageindex.api.model.TreeNode
import io.pageindex.core.prompt.PromptTemplate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class SummaryGenerator(
  private val structuredChatService: StructuredChatService,
  private val promptProvider: PromptProvider
) {

  private val logger = LoggerFactory.getLogger(SummaryGenerator::class.java)

  suspend fun generateSummaries(
    nodes: List<TreeNode>,
    summaryMaxTokens: Int,
    batchSize: Int,
    concurrency: Int = DEFAULT_SUMMARY_CONCURRENCY
  ): List<TreeNode> {
    val leafNodes = collectLeafNodes(nodes)
    if (leafNodes.isEmpty()) return nodes

    val leafSummaryMap = buildSummaryMap(leafNodes, batchSize, concurrency, PromptName.SUMMARY) { node ->
      "[Node ${node.nodeId}] Title: ${node.title}\nText: ${node.text.orEmpty()}"
    }
    val afterPhase1 = applySummaries(nodes, leafSummaryMap)

    val parentNodes = collectParentNodesBottomUp(afterPhase1)
    if (parentNodes.isEmpty()) return afterPhase1

    val rollupSummaryMap = buildSummaryMap(
      parentNodes, batchSize, concurrency, PromptName.ROLLUP_SUMMARY
    ) { node ->
      val childLines = node.children.joinToString("\n") { child ->
        "  - ${child.title}: ${child.summary.orEmpty().ifEmpty { "(no summary)" }}"
      }
      "[Node ${node.nodeId}] Title: ${node.title}\nSubsections:\n$childLines"
    }

    return applySummaries(afterPhase1, rollupSummaryMap)
  }

  private suspend fun buildSummaryMap(
    nodes: List<TreeNode>,
    batchSize: Int,
    concurrency: Int,
    promptName: PromptName,
    formatNode: (TreeNode) -> String
  ): Map<String, String> {
    val summaryMap = ConcurrentHashMap<String, String>()
    val semaphore = Semaphore(concurrency)

    coroutineScope {
      nodes.chunked(batchSize).map { batch ->
        async {
          semaphore.withPermit {
            summaryMap.putAll(processBatch(batch, promptName, formatNode))
          }
        }
      }.awaitAll()
    }

    return summaryMap
  }

  private suspend fun processBatch(
    batch: List<TreeNode>,
    promptName: PromptName,
    formatNode: (TreeNode) -> String
  ): Map<String, String> {
    val sectionsText = batch.joinToString("\n\n", transform = formatNode)
    val prompt = PromptTemplate.render(promptProvider, promptName, "sections" to sectionsText)
    val messages = listOf(LlmMessage(LlmRole.USER, prompt))

    return structuredChatService.chatList(
      messages = messages,
      elementType = SummaryEntry::class.java,
      tags = listOf("summary", "page-index")
    ).fold(
      ifLeft = {
        logger.warn("Summary batch (${promptName.name}) failed: ${it.message}")
        emptyMap()
      },
      ifRight = { entries ->
        entries.mapNotNull { entry ->
          val nodeId = entry.nodeId ?: return@mapNotNull null
          nodeId to entry.summary
        }.toMap()
      }
    )
  }

  private fun collectLeafNodes(nodes: List<TreeNode>): List<TreeNode> {
    val result = mutableListOf<TreeNode>()
    for (node in nodes) {
      if (node.children.isEmpty()) {
        result.add(node)
      } else {
        result.addAll(collectLeafNodes(node.children))
      }
    }
    return result
  }

  private fun collectParentNodesBottomUp(nodes: List<TreeNode>): List<TreeNode> {
    val result = mutableListOf<TreeNode>()
    for (node in nodes) {
      if (node.children.isNotEmpty()) {
        result.addAll(collectParentNodesBottomUp(node.children))
        result.add(node)
      }
    }
    return result
  }

  private fun applySummaries(nodes: List<TreeNode>, summaryMap: Map<String, String>): List<TreeNode> {
    return nodes.map { node ->
      val updatedChildren = applySummaries(node.children, summaryMap)
      val summary = summaryMap[node.nodeId]
      if (summary != null || updatedChildren !== node.children) {
        node.copy(summary = summary ?: node.summary, children = updatedChildren)
      } else {
        node
      }
    }
  }

  private companion object {
    private const val DEFAULT_SUMMARY_CONCURRENCY = 5
  }
}
