package com.c0x12c.pageindex.core.embedding

import com.c0x12c.pageindex.api.NodeEmbeddingService

/**
 * A no-op embedding service that returns empty arrays.
 *
 * Use this when you don't need embedding-based hybrid retrieval and only want
 * LLM-based node selection via [DefaultNodeRetriever].
 */
class NoOpEmbeddingService : NodeEmbeddingService {
  override suspend fun embed(texts: List<String>): List<FloatArray> = texts.map { floatArrayOf() }
  override suspend fun embedSingle(text: String): FloatArray = floatArrayOf()
}
