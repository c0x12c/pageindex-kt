package io.pageindex.core

import arrow.core.Either
import io.pageindex.api.PageIndexException
import io.pageindex.api.model.DocumentTree
import io.pageindex.api.model.LlmMessage
import io.pageindex.api.model.RetrievedContext

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
