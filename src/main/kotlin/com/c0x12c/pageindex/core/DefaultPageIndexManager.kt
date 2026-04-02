package com.c0x12c.pageindex.core

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.api.DocumentTreeStore
import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.PageIndexManager
import com.c0x12c.pageindex.api.model.DocumentQueryResult
import com.c0x12c.pageindex.api.model.DocumentTree
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.api.model.PageIndexError
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.core.chat.StructuredChatService
import com.c0x12c.pageindex.core.indexer.TreeIndexBuilder
import com.c0x12c.pageindex.core.model.QaResult
import com.c0x12c.pageindex.core.retriever.NodeRetriever
import com.c0x12c.pageindex.api.model.RetrievedContext
import java.util.UUID

class DefaultPageIndexManager(
  private val treeIndexBuilder: TreeIndexBuilder,
  private val nodeRetriever: NodeRetriever,
  private val structuredChatService: StructuredChatService,
  private val llmClient: LlmClient,
  private val documentTreeStore: DocumentTreeStore
) : PageIndexManager {

  override suspend fun buildAndSave(
    documentId: UUID,
    projectId: UUID,
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): Either<PageIndexException, DocumentTree> {
    val existing = documentTreeStore.findByDocumentId(documentId)
    if (existing != null) {
      return existing.right()
    }

    val tree = treeIndexBuilder.buildIndex(documentId, projectId, pages, config)
      .getOrElse { return it.left() }

    documentTreeStore.save(tree)
    return tree.right()
  }

  override suspend fun search(
    documentId: UUID,
    messages: List<LlmMessage>
  ): Either<PageIndexException, RetrievedContext> {
    val tree = documentTreeStore.findByDocumentId(documentId)
      ?: return PageIndexError.INDEX_NOT_FOUND.asException().left()

    return nodeRetriever.retrieve(tree, messages)
  }

  override suspend fun <T> query(
    documentId: UUID,
    messages: List<LlmMessage>,
    responseType: Class<T>
  ): Either<PageIndexException, DocumentQueryResult<T>> {
    val tree = documentTreeStore.findByDocumentId(documentId)
      ?: return PageIndexError.INDEX_NOT_FOUND.asException().left()

    val context = nodeRetriever.retrieve(tree, messages)
      .getOrElse { return it.left() }

    if (context.selectedNodeIds.isEmpty()) {
      return PageIndexError.INDEX_NOT_FOUND.asException().left()
    }

    val contextMessage = LlmMessage(
      LlmRole.SYSTEM,
      "Based on the following document sections, extract the requested information.\n\n" +
        context.sectionsText
    )

    val data = structuredChatService.chat(
      messages = listOf(contextMessage) + messages,
      responseType = responseType,
      tags = listOf("tree-query", "page-index")
    ).getOrElse { return it.left() }

    return DocumentQueryResult(
      data = data,
      selectedNodeIds = context.selectedNodeIds,
      reasoning = context.reasoning,
      sourcePages = context.sourcePages
    ).right()
  }

  override suspend fun query(
    documentId: UUID,
    messages: List<LlmMessage>
  ): Either<PageIndexException, DocumentQueryResult<String>> {
    val tree = documentTreeStore.findByDocumentId(documentId)
      ?: return PageIndexError.INDEX_NOT_FOUND.asException().left()

    val context = nodeRetriever.retrieve(tree, messages)
      .getOrElse { return it.left() }

    if (context.selectedNodeIds.isEmpty()) {
      return DocumentQueryResult(
        data = "No relevant sections found in the document for this query.",
        selectedNodeIds = emptyList(),
        reasoning = context.reasoning,
        sourcePages = emptyList()
      ).right()
    }

    val contextMessage = LlmMessage(
      LlmRole.SYSTEM,
      "Based on the following document sections, answer the user's question.\n\n" +
        context.sectionsText
    )

    val result = structuredChatService.chat(
      messages = listOf(contextMessage) + messages,
      responseType = QaResult::class.java,
      tags = listOf("text-query", "page-index")
    ).getOrElse { return it.left() }

    return DocumentQueryResult(
      data = result.answer,
      selectedNodeIds = context.selectedNodeIds,
      reasoning = result.reasoning,
      sourcePages = context.sourcePages
    ).right()
  }
}
