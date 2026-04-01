package io.pageindex.core

import arrow.core.Either
import io.pageindex.api.PageIndexException
import io.pageindex.api.model.DocumentTree
import io.pageindex.api.model.IndexingConfig
import io.pageindex.api.model.ParsedPage
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
