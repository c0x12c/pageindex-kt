package com.c0x12c.pageindex.core.chat

import arrow.core.Either
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.api.model.LlmMessage

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
