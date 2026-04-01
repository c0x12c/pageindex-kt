package io.pageindex.core

import arrow.core.Either
import io.pageindex.api.PageIndexException
import io.pageindex.api.model.LlmMessage

/**
 * Wraps an [LlmClient] to return parsed JSON objects.
 *
 * Sends schema hints to the LLM and deserializes the response.
 * Retries once with a corrective prompt if parsing fails.
 */
interface StructuredChatService {
  suspend fun <T> chat(
    messages: List<LlmMessage>,
    responseType: Class<T>,
    tags: List<String> = emptyList()
  ): Either<PageIndexException, T>

  suspend fun <T> chatList(
    messages: List<LlmMessage>,
    elementType: Class<T>,
    tags: List<String> = emptyList()
  ): Either<PageIndexException, List<T>>
}
