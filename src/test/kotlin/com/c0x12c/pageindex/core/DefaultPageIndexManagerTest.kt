package com.c0x12c.pageindex.core

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.core.chat.StructuredChatService
import com.c0x12c.pageindex.core.indexer.TreeIndexBuilder
import com.c0x12c.pageindex.core.retriever.NodeRetriever
import com.c0x12c.pageindex.api.model.DocumentTree
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.api.model.PageIndexError
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.core.model.QaResult
import com.c0x12c.pageindex.api.model.RetrievedContext
import com.c0x12c.pageindex.api.model.TreeMetadata
import com.c0x12c.pageindex.api.model.TreeNode
import com.c0x12c.pageindex.core.detector.testConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DefaultPageIndexManagerTest {

  private fun fakeTree(
    documentId: UUID = UUID.randomUUID(),
    projectId: UUID = UUID.randomUUID()
  ) = DocumentTree(
    documentId = documentId,
    projectId = projectId,
    rootNodes = listOf(
      TreeNode(nodeId = "0001", title = "Section 1", level = 1, startPage = 1, endPage = 3)
    ),
    metadata = TreeMetadata(
      totalNodes = 1,
      totalPages = 3,
      maxDepth = 1,
      indexingModel = "page-index",
      indexingMethod = IndexingMethod.FLAT_PAGES
    )
  )

  private fun fakeContext() = RetrievedContext(
    selectedNodeIds = listOf("0001"),
    reasoning = "Found relevant content",
    sectionsText = "Section 1 text",
    sourcePages = listOf(1, 2)
  )

  private fun successBuilder(tree: DocumentTree) = object : TreeIndexBuilder {
    override suspend fun buildIndex(
      documentId: UUID,
      projectId: UUID,
      pages: List<ParsedPage>,
      config: IndexingConfig
    ): Either<PageIndexException, DocumentTree> = tree.right()
  }

  private fun failingBuilder() = object : TreeIndexBuilder {
    override suspend fun buildIndex(
      documentId: UUID,
      projectId: UUID,
      pages: List<ParsedPage>,
      config: IndexingConfig
    ): Either<PageIndexException, DocumentTree> =
      PageIndexError.STRUCTURE_DETECTION_FAILED.asException().left()
  }

  private fun successRetriever(context: RetrievedContext) = object : NodeRetriever {
    override suspend fun retrieve(
      tree: DocumentTree,
      messages: List<LlmMessage>
    ): Either<PageIndexException, RetrievedContext> = context.right()
  }

  private fun noopStructuredChat() = object : StructuredChatService {
    override suspend fun <T> chat(
      messages: List<LlmMessage>,
      responseType: Class<T>,
      tags: List<String>
    ): Either<PageIndexException, T> =
      PageIndexError.STRUCTURED_CHAT_PARSE_FAILED.asException().left()

    override suspend fun <T> chatList(
      messages: List<LlmMessage>,
      elementType: Class<T>,
      tags: List<String>
    ): Either<PageIndexException, List<T>> =
      PageIndexError.STRUCTURED_CHAT_PARSE_FAILED.asException().left()
  }

  private fun noopLlmClient() = object : LlmClient {
    override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String = ""
  }

  private fun echoLlmClient() = object : LlmClient {
    override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
      "The answer based on context"
  }

  private fun qaStructuredChat(answer: String, reasoning: String = "Found relevant content") =
    object : StructuredChatService {
      override suspend fun <T> chat(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): Either<PageIndexException, T> =
        if (responseType == QaResult::class.java) {
          @Suppress("UNCHECKED_CAST")
          (QaResult(answer, reasoning) as T).right()
        } else {
          PageIndexError.STRUCTURED_CHAT_PARSE_FAILED.asException().left()
        }

      override suspend fun <T> chatList(
        messages: List<LlmMessage>,
        elementType: Class<T>,
        tags: List<String>
      ): Either<PageIndexException, List<T>> =
        PageIndexError.STRUCTURED_CHAT_PARSE_FAILED.asException().left()
    }

  private fun manager(
    builder: TreeIndexBuilder,
    retriever: NodeRetriever = successRetriever(fakeContext()),
    structuredChat: StructuredChatService = noopStructuredChat(),
    llmClient: LlmClient = noopLlmClient(),
    store: InMemoryDocumentTreeStore = InMemoryDocumentTreeStore()
  ) = DefaultPageIndexManager(builder, retriever, structuredChat, llmClient, store) to store

  @Test
  fun `buildAndSave - builds and saves tree`() = runTest {
    val documentId = UUID.randomUUID()
    val tree = fakeTree(documentId)
    val (mgr, store) = manager(successBuilder(tree))

    val result = mgr.buildAndSave(documentId, UUID.randomUUID(), emptyList(), testConfig())

    assertTrue(result is Either.Right)
    val saved = (result as Either.Right).value
    assertEquals(documentId, saved.documentId)
    assertNotNull(store.findByDocumentId(documentId))
  }

  @Test
  fun `buildAndSave - returns existing tree if already indexed`() = runTest {
    val documentId = UUID.randomUUID()
    val existingTree = fakeTree(documentId)
    val store = InMemoryDocumentTreeStore()
    store.save(existingTree)

    var builderCalled = false
    val builder = object : TreeIndexBuilder {
      override suspend fun buildIndex(
        documentId: UUID,
        projectId: UUID,
        pages: List<ParsedPage>,
        config: IndexingConfig
      ): Either<PageIndexException, DocumentTree> {
        builderCalled = true
        return fakeTree(documentId).right()
      }
    }

    val mgr = DefaultPageIndexManager(
      builder, successRetriever(fakeContext()), noopStructuredChat(), noopLlmClient(), store
    )

    val result = mgr.buildAndSave(documentId, UUID.randomUUID(), emptyList(), testConfig())

    assertTrue(result is Either.Right)
    assertEquals(existingTree.documentId, (result as Either.Right).value.documentId)
    assertEquals(false, builderCalled)
  }

  @Test
  fun `buildAndSave - returns error when builder fails`() = runTest {
    val (mgr, _) = manager(failingBuilder())

    val result = mgr.buildAndSave(UUID.randomUUID(), UUID.randomUUID(), emptyList(), testConfig())

    assertTrue(result is Either.Left)
    val error = (result as Either.Left).value
    assertEquals("PAGE_INDEX_STRUCTURE_FAILED", error.code)
  }

  @Test
  fun `search - returns retrieved context for existing tree`() = runTest {
    val documentId = UUID.randomUUID()
    val tree = fakeTree(documentId)
    val store = InMemoryDocumentTreeStore()
    store.save(tree)

    val context = fakeContext()
    val mgr = DefaultPageIndexManager(
      failingBuilder(), successRetriever(context), noopStructuredChat(), noopLlmClient(), store
    )

    val messages = listOf(LlmMessage(LlmRole.USER, "What is section 1 about?"))
    val result = mgr.search(documentId, messages)

    assertTrue(result is Either.Right)
    val found = (result as Either.Right).value
    assertEquals(context.selectedNodeIds, found.selectedNodeIds)
    assertEquals(context.reasoning, found.reasoning)
  }

  @Test
  fun `search - returns INDEX_NOT_FOUND when no tree exists`() = runTest {
    val (mgr, _) = manager(failingBuilder())

    val messages = listOf(LlmMessage(LlmRole.USER, "query"))
    val result = mgr.search(UUID.randomUUID(), messages)

    assertTrue(result is Either.Left)
    val error = (result as Either.Left).value
    assertEquals("PAGE_INDEX_NOT_FOUND", error.code)
  }

  @Test
  fun `query text - returns answer for existing tree`() = runTest {
    val documentId = UUID.randomUUID()
    val tree = fakeTree(documentId)
    val store = InMemoryDocumentTreeStore()
    store.save(tree)

    val mgr = DefaultPageIndexManager(
      failingBuilder(),
      successRetriever(fakeContext()),
      qaStructuredChat("The answer based on context"),
      echoLlmClient(),
      store
    )

    val messages = listOf(LlmMessage(LlmRole.USER, "What is the risk?"))
    val result = mgr.query(documentId, messages)

    assertTrue(result is Either.Right)
    val found = (result as Either.Right).value
    assertEquals("The answer based on context", found.data)
    assertEquals(listOf("0001"), found.selectedNodeIds)
    assertEquals("Found relevant content", found.reasoning)
  }

  @Test
  fun `query text - returns empty answer when no nodes found`() = runTest {
    val documentId = UUID.randomUUID()
    val tree = fakeTree(documentId)
    val store = InMemoryDocumentTreeStore()
    store.save(tree)

    val emptyContext = RetrievedContext(
      selectedNodeIds = emptyList(),
      reasoning = "No match",
      sectionsText = "",
      sourcePages = emptyList()
    )

    val mgr = DefaultPageIndexManager(
      failingBuilder(), successRetriever(emptyContext), noopStructuredChat(), noopLlmClient(), store
    )

    val messages = listOf(LlmMessage(LlmRole.USER, "irrelevant query"))
    val result = mgr.query(documentId, messages)

    assertTrue(result is Either.Right)
    val found = (result as Either.Right).value
    assertEquals("No relevant sections found in the document for this query.", found.data)
    assertEquals(emptyList<String>(), found.selectedNodeIds)
  }
}
