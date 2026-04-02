package com.c0x12c.pageindex.core.retriever

import kotlin.math.ln

/**
 * In-memory BM25 scorer over a small corpus of document nodes.
 * Suitable for the typical ESA document tree size (< 200 nodes).
 *
 * Parameters follow the standard BM25 defaults: k1=1.2, b=0.75.
 */
object BM25Scorer {

  data class ScoredNode(val nodeId: String, val score: Double)

  /**
   * Scores each node in [corpus] against the [query].
   * Returns nodes sorted by descending score (highest relevance first).
   *
   * @param corpus list of (nodeId, searchableText) pairs
   */
  fun score(query: String, corpus: List<Pair<String, String>>): List<ScoredNode> {
    if (corpus.isEmpty()) return emptyList()

    val queryTerms = tokenize(query)
    if (queryTerms.isEmpty()) return corpus.map { ScoredNode(it.first, 0.0) }

    val tokenizedCorpus = corpus.map { (nodeId, text) -> nodeId to tokenize(text) }
    val avgDl = tokenizedCorpus.map { it.second.size }.average().coerceAtLeast(1.0)
    val N = corpus.size.toDouble()

    // Document frequency per term
    val df = mutableMapOf<String, Int>()
    for ((_, tokens) in tokenizedCorpus) {
      tokens.toSet().forEach { term -> df[term] = (df[term] ?: 0) + 1 }
    }

    return tokenizedCorpus.map { (nodeId, tokens) ->
      val dl = tokens.size.toDouble()
      val tf = mutableMapOf<String, Int>()
      tokens.forEach { term -> tf[term] = (tf[term] ?: 0) + 1 }

      val score = queryTerms.sumOf { term ->
        val termTf = (tf[term] ?: 0).toDouble()
        val termDf = (df[term] ?: 0).toDouble()
        if (termDf == 0.0) return@sumOf 0.0

        val idf = ln((N - termDf + 0.5) / (termDf + 0.5) + 1.0)
        val tfNorm = (termTf * (K1 + 1)) / (termTf + K1 * (1 - B + B * dl / avgDl))
        idf * tfNorm
      }

      ScoredNode(nodeId, score)
    }.sortedByDescending { it.score }
  }

  private fun tokenize(text: String): List<String> =
    text.lowercase()
      .replace(Regex("[^a-z0-9\\s]"), " ")
      .split(Regex("\\s+"))
      .filter { it.length >= MIN_TOKEN_LENGTH }

  private const val K1 = 1.2
  private const val B = 0.75
  private const val MIN_TOKEN_LENGTH = 2
}
