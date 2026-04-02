package com.c0x12c.pageindex.core.indexer

import arrow.core.Either
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.api.model.DocumentTree
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.ParsedPage
import java.util.UUID

/**
 * Builds a hierarchical [DocumentTree] from parsed document pages.
 *
 * Detects document structure, splits oversized nodes, generates summaries,
 * and computes embeddings.
 */
interface TreeIndexBuilder {
  suspend fun buildIndex(
    documentId: UUID,
    projectId: UUID,
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): Either<PageIndexException, DocumentTree>
}
