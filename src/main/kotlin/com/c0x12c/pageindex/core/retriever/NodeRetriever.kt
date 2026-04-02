package com.c0x12c.pageindex.core.retriever

import arrow.core.Either
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.api.model.DocumentTree
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.RetrievedContext

/**
 * Selects relevant nodes from a [DocumentTree] based on user messages.
 *
 * The LLM reads a compact tree representation and picks the most relevant sections.
 */
interface NodeRetriever {
  suspend fun retrieve(
    tree: DocumentTree,
    messages: List<LlmMessage>
  ): Either<PageIndexException, RetrievedContext>
}
