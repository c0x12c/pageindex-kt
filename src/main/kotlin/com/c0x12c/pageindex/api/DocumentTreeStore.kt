package com.c0x12c.pageindex.api

import com.c0x12c.pageindex.api.model.DocumentTree
import java.util.UUID

/**
 * Persistence layer for [DocumentTree] objects.
 *
 * Implement this for your storage backend (database, file system, etc.).
 * Use [com.c0x12c.pageindex.core.InMemoryDocumentTreeStore] for testing and prototyping.
 */
interface DocumentTreeStore {
  suspend fun save(tree: DocumentTree)
  suspend fun findByDocumentId(documentId: UUID): DocumentTree?
  suspend fun deleteByDocumentId(documentId: UUID)
}
