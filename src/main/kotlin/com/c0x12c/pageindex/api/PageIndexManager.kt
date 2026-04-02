package com.c0x12c.pageindex.api

import arrow.core.Either
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.api.model.DocumentQueryResult
import com.c0x12c.pageindex.api.model.DocumentTree
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.RetrievedContext
import java.util.UUID

/**
 * Main entry point for document indexing and retrieval.
 *
 * Build a hierarchical index from document pages, then search or query it.
 * Use [com.c0x12c.pageindex.core.PageIndex.create] for easy setup.
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
