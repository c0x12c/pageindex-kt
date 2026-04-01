package io.pageindex.core

import io.pageindex.api.DocumentTreeStore
import io.pageindex.api.LlmClient
import io.pageindex.api.NodeEmbeddingService
import io.pageindex.api.PageIndexManager
import io.pageindex.api.PromptProvider
import io.pageindex.api.StructureDetector
import io.pageindex.core.detector.ChainedStructureDetector
import io.pageindex.core.detector.FlatPagesFallback
import io.pageindex.core.detector.LlmStructureDetector
import io.pageindex.core.detector.MarkdownHeaderDetector
import io.pageindex.core.detector.RegexTocDetector
import io.pageindex.core.detector.TocNoPageNumbersDetector
import io.pageindex.core.prompt.ResourcePromptProvider

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
  fun maxNodes(maxNodes: Int) = apply { this.maxNodes = maxNodes }

  /**
   * Build the [PageIndexManager].
   *
   * @throws IllegalStateException if [llmClient] is not set.
   */
  fun build(): PageIndexManager {
    val llm = requireNotNull(llmClient) { "llmClient is required. Set it via llmClient(yourClient) or llmClient = yourClient" }

    val prompts = promptProvider ?: ResourcePromptProvider()
    val structured = structuredChatService ?: DefaultStructuredChatService(llm)
    val embeddings = embeddingService ?: NoOpEmbeddingService()
    val store = documentTreeStore ?: InMemoryDocumentTreeStore()

    val detector = structureDetector ?: ChainedStructureDetector(
      listOf(
        RegexTocDetector(),
        MarkdownHeaderDetector(),
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
