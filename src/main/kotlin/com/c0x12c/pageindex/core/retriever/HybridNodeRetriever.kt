package com.c0x12c.pageindex.core.retriever

import arrow.core.Either
import arrow.core.right
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.api.NodeEmbeddingService
import com.c0x12c.pageindex.api.model.DocumentTree
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.api.model.RetrievedContext
import com.c0x12c.pageindex.api.model.TreeNode
import java.util.IdentityHashMap
import kotlin.math.sqrt

class HybridNodeRetriever(
  private val embeddingService: NodeEmbeddingService,
  private val maxNodes: Int = 5
) : NodeRetriever {

  override suspend fun retrieve(
    tree: DocumentTree,
    messages: List<LlmMessage>
  ): Either<PageIndexException, RetrievedContext> {
    val query = messages.lastOrNull { it.role == LlmRole.USER }?.content.orEmpty()
    val allNodes = flattenTree(tree.rootNodes)
    if (allNodes.isEmpty()) {
      return RetrievedContext(
        selectedNodeIds = emptyList(),
        reasoning = "Document tree is empty.",
        sectionsText = "",
        sourcePages = emptyList()
      ).right()
    }

    val corpus = allNodes.map { node -> node.nodeId to buildSearchText(node) }
    val bm25Ranked = BM25Scorer.score(query, corpus)

    val queryEmbedding = embeddingService.embedSingle(query)
    val cosineRanked = allNodes
      .mapNotNull { node ->
        val emb = tree.nodeEmbeddings[node.nodeId] ?: return@mapNotNull null
        node.nodeId to cosineSimilarity(queryEmbedding, emb).toDouble()
      }
      .sortedByDescending { it.second }

    val ranked = rrfMerge(bm25Ranked, cosineRanked).take(maxNodes)
    val nodeById = allNodes.associateBy { it.nodeId }
    val topNodes = ranked.mapNotNull { nodeById[it.nodeId] }

    val contextLeaves = topNodes
      .flatMap { expandToLeaves(it) }
      .let { leaves ->
        val seen = IdentityHashMap<TreeNode, Unit>()
        leaves.filter { seen.put(it, Unit) == null }
      }
      .sortedBy { it.startPage }

    val sectionsText = buildContextText(contextLeaves)
    val sourcePages = contextLeaves
      .flatMap { (it.startPage..it.endPage).toList() }
      .distinct()
      .sorted()

    return RetrievedContext(
      selectedNodeIds = topNodes.map { it.nodeId },
      reasoning = "",
      sectionsText = sectionsText,
      sourcePages = sourcePages
    ).right()
  }

  private fun expandToLeaves(node: TreeNode): List<TreeNode> =
    if (node.children.isEmpty()) listOf(node)
    else node.children.flatMap { expandToLeaves(it) }

  private fun buildContextText(leaves: List<TreeNode>): String =
    leaves.joinToString("\n\n") { node ->
      val breadcrumb = buildBreadcrumb(node)
      "--- $breadcrumb (Pages ${node.startPage}-${node.endPage}) ---\n${node.text.orEmpty()}"
    }

  private fun buildBreadcrumb(node: TreeNode): String {
    val path = mutableListOf<String>()
    var current: TreeNode? = node
    while (current != null) {
      path.add(0, current.title)
      current = current.parent
    }
    return path.joinToString(" > ")
  }

  private fun buildSearchText(node: TreeNode): String =
    if (node.children.isEmpty()) {
      "${node.title}. ${node.text.orEmpty()}"
    } else {
      node.title
    }

  private fun flattenTree(nodes: List<TreeNode>): List<TreeNode> {
    val result = mutableListOf<TreeNode>()
    for (node in nodes) {
      result.add(node)
      result.addAll(flattenTree(node.children))
    }
    return result
  }

  companion object {
    private const val RRF_K = 60

    data class RankedNode(val nodeId: String, val score: Double)

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
      var dot = 0f
      var normA = 0f
      var normB = 0f
      for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
      }
      val denom = sqrt(normA) * sqrt(normB)
      return if (denom == 0f) 0f else dot / denom
    }

    fun rrfMerge(
      bm25Scores: List<BM25Scorer.ScoredNode>,
      cosineScores: List<Pair<String, Double>>
    ): List<RankedNode> {
      val bm25Rank = bm25Scores.mapIndexed { rank, node -> node.nodeId to rank }.toMap()
      val cosineRank = cosineScores.mapIndexed { rank, (nodeId, _) -> nodeId to rank }.toMap()

      val allIds = (bm25Rank.keys + cosineRank.keys).toSet()
      return allIds.map { nodeId ->
        val bm25Score = bm25Rank[nodeId]?.let { 1.0 / (RRF_K + it) } ?: 0.0
        val cosineScore = cosineRank[nodeId]?.let { 1.0 / (RRF_K + it) } ?: 0.0
        RankedNode(nodeId, bm25Score + cosineScore)
      }.sortedByDescending { it.score }
    }
  }
}
