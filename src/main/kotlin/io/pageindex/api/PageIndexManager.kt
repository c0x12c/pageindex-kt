package io.pageindex.api

import arrow.core.Either
import io.pageindex.api.PageIndexException
import io.pageindex.api.model.DocumentQueryResult
import io.pageindex.api.model.DocumentTree
import io.pageindex.api.model.IndexingConfig
import io.pageindex.api.model.LlmMessage
import io.pageindex.api.model.ParsedPage
import io.pageindex.api.model.RetrievedContext
import java.util.UUID

/**
 * Main entry point for document indexing and retrieval.
 *
 * Build a hierarchical index from document pages, then search or query it.
 * Use [io.pageindex.core.PageIndex.create] for easy setup.
 */
interface PageIndexManager {
  suspend fun buildAndSave(
    documentId: UUID,
    projectId: UUID,
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): Either<PageIndexException, DocumentTree>

  suspend fun search(
    documentId: UUID,
    messages: List<LlmMessage>
  ): Either<PageIndexException, RetrievedContext>

  suspend fun <T> query(
    documentId: UUID,
    messages: List<LlmMessage>,
    responseType: Class<T>
  ): Either<PageIndexException, DocumentQueryResult<T>>

  suspend fun query(
    documentId: UUID,
    messages: List<LlmMessage>
  ): Either<PageIndexException, DocumentQueryResult<String>>
}
