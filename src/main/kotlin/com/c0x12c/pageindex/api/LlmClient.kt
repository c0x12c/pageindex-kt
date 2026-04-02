package com.c0x12c.pageindex.api

import com.c0x12c.pageindex.api.model.LlmMessage

/**
 * Chat interface for any LLM provider (OpenAI, Claude, Gemini, local models, etc.).
 *
 * Implement [chat] with your LLM call. The [chatStructured] and [chatStructuredList]
 * methods default to [chat] but can be overridden for providers with native JSON mode.
 */
interface LlmClient {
  suspend fun chat(messages: List<LlmMessage>, tags: List<String> = emptyList()): String

  suspend fun <T> chatStructured(
    messages: List<LlmMessage>,
    responseType: Class<T>,
    tags: List<String> = emptyList()
  ): String = chat(messages, tags)

  suspend fun <T> chatStructuredList(
    messages: List<LlmMessage>,
    elementType: Class<T>,
    tags: List<String> = emptyList()
  ): String = chat(messages, tags)
}
