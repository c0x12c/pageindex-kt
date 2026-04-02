package com.c0x12c.pageindex.core

import com.c0x12c.pageindex.api.DocumentTreeStore
import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.NodeEmbeddingService
import com.c0x12c.pageindex.api.PageIndexManager
import com.c0x12c.pageindex.api.PromptProvider
import com.c0x12c.pageindex.api.RegexPatternCache
import com.c0x12c.pageindex.api.StructureDetector
import com.c0x12c.pageindex.core.chat.DefaultStructuredChatService
import com.c0x12c.pageindex.core.chat.StructuredChatService
import com.c0x12c.pageindex.core.detector.ChainedStructureDetector
import com.c0x12c.pageindex.core.detector.FlatPagesFallback
import com.c0x12c.pageindex.core.detector.LlmRegexDiscoveryDetector
import com.c0x12c.pageindex.core.detector.LlmStructureDetector
import com.c0x12c.pageindex.core.detector.MarkdownRegexDetector
import com.c0x12c.pageindex.core.detector.RegexTocDetector
import com.c0x12c.pageindex.core.detector.TocNoPageNumbersDetector
import com.c0x12c.pageindex.core.embedding.NoOpEmbeddingService
import com.c0x12c.pageindex.core.indexer.DefaultTreeIndexBuilder
import com.c0x12c.pageindex.core.indexer.TreeIndexBuilder
import com.c0x12c.pageindex.core.prompt.ResourcePromptProvider
import com.c0x12c.pageindex.core.retriever.DefaultNodeRetriever
import com.c0x12c.pageindex.core.retriever.NodeRetriever
import com.c0x12c.pageindex.core.store.InMemoryDocumentTreeStore

/**
 * Builder for creating a [PageIndexManager] with minimal setup.
 *
 * Only [llmClient] is required. All other components use sensible defaults.
 *
 * ```kotlin
 * val pageIndex = PageIndex.builder()
 *   .llmClient(myLlmClient)
 *   .build()
 * ```
 *
 * Or with Kotlin DSL:
 * ```kotlin
 * val pageIndex = PageIndex.create {
 *   llmClient = myLlmClient
 *   maxNodes = 10
 * }
 * ```
 */
class PageIndexBuilder {
  /** Required. The LLM client for chat operations. */
  var llmClient: LlmClient? = null

  /** Optional. Custom embedding service. Defaults to [NoOpEmbeddingService]. */
  var embeddingService: NodeEmbeddingService? = null

  /** Optional. Custom document tree store. Defaults to [InMemoryDocumentTreeStore]. */
  var documentTreeStore: DocumentTreeStore? = null

  /** Optional. Custom structure detector. Defaults to the full detection chain. */
  var structureDetector: StructureDetector? = null

  /** Optional. Custom structured chat service. Built from [llmClient] if not set. */
  var structuredChatService: StructuredChatService? = null

  /** Optional. Custom prompt provider. Defaults to [ResourcePromptProvider]. */
  var promptProvider: PromptProvider? = null

  /** Optional. Custom tree index builder. Built from detector + embedding service if not set. */
  var treeIndexBuilder: TreeIndexBuilder? = null

  /** Optional. Custom node retriever. Built from structured chat service if not set. */
  var nodeRetriever: NodeRetriever? = null

  /** Optional. Cache for LLM-discovered regex patterns. */
  var regexPatternCache: RegexPatternCache? = null

  /** Maximum nodes to select per query. Default: 5. */
  var maxNodes: Int = 5

  // Java-friendly fluent setters

  fun llmClient(llmClient: LlmClient) = apply { this.llmClient = llmClient }
  fun embeddingService(service: NodeEmbeddingService) = apply { this.embeddingService = service }
  fun documentTreeStore(store: DocumentTreeStore) = apply { this.documentTreeStore = store }
  fun structureDetector(detector: StructureDetector) = apply { this.structureDetector = detector }
  fun structuredChatService(service: StructuredChatService) = apply { this.structuredChatService = service }
  fun promptProvider(provider: PromptProvider) = apply { this.promptProvider = provider }
  fun treeIndexBuilder(builder: TreeIndexBuilder) = apply { this.treeIndexBuilder = builder }
  fun nodeRetriever(retriever: NodeRetriever) = apply { this.nodeRetriever = retriever }
  fun regexPatternCache(cache: RegexPatternCache) = apply { this.regexPatternCache = cache }
  fun maxNodes(maxNodes: Int) = apply { this.maxNodes = maxNodes }

  /**
   * Build the [PageIndexManager].
   *
   * @throws IllegalStateException if [llmClient] is not set.
   */
  fun build(): PageIndexManager {
    val llm = requireNotNull(llmClient) { "llmClient is required. Set it via llmClient(yourClient) or llmClient = yourClient" }
    require(maxNodes > 0) { "maxNodes must be positive, got $maxNodes" }

    val prompts = promptProvider ?: ResourcePromptProvider()
    val structured = structuredChatService ?: DefaultStructuredChatService(llm)
    val embeddings = embeddingService ?: NoOpEmbeddingService()
    val store = documentTreeStore ?: InMemoryDocumentTreeStore()

    val detector = structureDetector ?: ChainedStructureDetector(
      listOf(
        RegexTocDetector(),
        MarkdownRegexDetector(),
        LlmRegexDiscoveryDetector(llm, prompts, regexPatternCache),
        TocNoPageNumbersDetector(llm, prompts),
        LlmStructureDetector(llm, prompts),
        FlatPagesFallback()
      )
    )

    val indexBuilder = treeIndexBuilder ?: DefaultTreeIndexBuilder(detector, embeddings)
    val retriever = nodeRetriever ?: DefaultNodeRetriever(structured, maxNodes)

    return DefaultPageIndexManager(indexBuilder, retriever, structured, llm, store)
  }
}

/**
 * Entry point for creating a [PageIndexManager].
 *
 * ```kotlin
 * // Kotlin DSL
 * val pageIndex = PageIndex.create {
 *   llmClient = myLlmClient
 * }
 *
 * // Java-style builder
 * val pageIndex = PageIndex.builder()
 *   .llmClient(myLlmClient)
 *   .build()
 * ```
 */
object PageIndex {
  /** Create a [PageIndexManager] using the Kotlin DSL. */
  fun create(block: PageIndexBuilder.() -> Unit): PageIndexManager {
    return PageIndexBuilder().apply(block).build()
  }

  /** Get a builder for Java-style fluent configuration. */
  fun builder(): PageIndexBuilder = PageIndexBuilder()
}
