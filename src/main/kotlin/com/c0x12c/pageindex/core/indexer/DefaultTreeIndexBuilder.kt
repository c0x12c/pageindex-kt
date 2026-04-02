package com.c0x12c.pageindex.core.indexer

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.api.NodeEmbeddingService
import com.c0x12c.pageindex.api.StructureDetector
import com.c0x12c.pageindex.api.model.DocumentTree
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.PageIndexError
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.TreeMetadata
import com.c0x12c.pageindex.api.model.TreeNode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DefaultTreeIndexBuilder(
  private val structureDetector: StructureDetector,
  private val embeddingService: NodeEmbeddingService
) : TreeIndexBuilder {

  override suspend fun buildIndex(
    documentId: UUID,
    projectId: UUID,
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): Either<PageIndexException, DocumentTree> {
    if (pages.isEmpty()) {
      return PageIndexError.NO_PAGES_PROVIDED.asException().left()
    }

    val detectionResult = structureDetector.detect(pages, config)
      ?: return PageIndexError.STRUCTURE_DETECTION_FAILED.asException().left()

    val rawNodes = TreeBuilder.fromEntries(detectionResult.entries, pages, detectionResult.lastContentPage)
    val splitNodes = NodeSplitter.splitOversizedNodes(rawNodes, config.maxNodeTextLength)

    TreeNode.wireParents(splitNodes)

    val nodeEmbeddings = embedNodes(splitNodes, config.embeddingConcurrency)

    val metadata = TreeMetadata(
      totalNodes = countNodes(splitNodes),
      totalPages = pages.size,
      maxDepth = maxDepth(splitNodes),
      indexingModel = "page-index",
      indexingMethod = detectionResult.method
    )

    return DocumentTree(
      documentId = documentId,
      projectId = projectId,
      rootNodes = splitNodes,
      metadata = metadata,
      nodeEmbeddings = nodeEmbeddings
    ).right()
  }

  private suspend fun embedNodes(
    nodes: List<TreeNode>,
    concurrency: Int
  ): Map<String, FloatArray> {
    val allNodes = flattenNodes(nodes)
    val result = ConcurrentHashMap<String, FloatArray>()
    val semaphore = Semaphore(concurrency)

    // Phase (a): embed leaf nodes using raw text content — no summary compression loss
    val leafNodes = allNodes.filter { it.children.isEmpty() && it.text != null }
    if (leafNodes.isNotEmpty()) {
      coroutineScope {
        leafNodes.chunked(EMBEDDING_BATCH_SIZE).map { batch ->
          async {
            semaphore.withPermit {
              val texts = batch.map { "${it.title}: ${it.text}" }
              val embeddings = embeddingService.embed(texts)
              batch.zip(embeddings).forEach { (node, emb) -> result[node.nodeId] = emb }
            }
          }
        }.awaitAll()
      }
    }

    // Phase (b): pool parent embeddings bottom-up from direct children (O(N), not O(N×depth))
    for (node in allNodes.reversed()) {
      if (node.children.isEmpty()) continue
      val childVectors = node.children.mapNotNull { result[it.nodeId] }
      if (childVectors.isNotEmpty()) {
        result[node.nodeId] = meanPool(childVectors)
      }
    }

    return result
  }

  private fun meanPool(vectors: List<FloatArray>): FloatArray {
    val dim = vectors.first().size
    val result = FloatArray(dim)
    for (vec in vectors) for (i in vec.indices) result[i] += vec[i]
    val n = vectors.size.toFloat()
    for (i in result.indices) result[i] /= n
    return result
  }

  private fun flattenNodes(nodes: List<TreeNode>): List<TreeNode> {
    val result = mutableListOf<TreeNode>()
    for (node in nodes) {
      result.add(node)
      result.addAll(flattenNodes(node.children))
    }
    return result
  }

  private fun countNodes(nodes: List<TreeNode>): Int {
    return nodes.sumOf { 1 + countNodes(it.children) }
  }

  companion object {
    private const val EMBEDDING_BATCH_SIZE = 50
  }

  private fun maxDepth(nodes: List<TreeNode>): Int {
    if (nodes.isEmpty()) return 0
    return 1 + (nodes.maxOfOrNull { maxDepth(it.children) } ?: 0)
  }
}
