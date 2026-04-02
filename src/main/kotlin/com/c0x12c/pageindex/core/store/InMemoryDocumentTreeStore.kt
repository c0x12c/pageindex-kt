package com.c0x12c.pageindex.core.store

import com.c0x12c.pageindex.api.DocumentTreeStore
import com.c0x12c.pageindex.api.model.DocumentTree
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory implementation of [DocumentTreeStore].
 *
 * Suitable for testing, prototyping, and single-process applications.
 * Data is lost when the process exits. For production use with persistence,
 * implement [DocumentTreeStore] with your preferred storage backend.
 */
class InMemoryDocumentTreeStore : DocumentTreeStore {
  private val store = ConcurrentHashMap<UUID, DocumentTree>()

  override suspend fun save(tree: DocumentTree) {
    store[tree.documentId] = tree
  }

  override suspend fun findByDocumentId(documentId: UUID): DocumentTree? {
    return store[documentId]
  }

  override suspend fun deleteByDocumentId(documentId: UUID) {
    store.remove(documentId)
  }
}
