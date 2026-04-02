package com.c0x12c.pageindex.core.detector

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.core.chat.StructuredChatService
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.PageIndexError
import com.c0x12c.pageindex.core.util.StructuredResponseJson

class FakeStructuredChatService(
  private val responses: MutableMap<String, String> = mutableMapOf(),
  private val defaultResponse: String = "[]"
) : StructuredChatService {

  private val calls = mutableListOf<Pair<List<LlmMessage>, List<String>>>()

  override suspend fun <T> chat(
    messages: List<LlmMessage>,
    responseType: Class<T>,
    tags: List<String>
  ): Either<PageIndexException, T> {
    calls.add(messages to tags)
    val raw = resolveResponse(tags)
    val parsed = StructuredResponseJson.readObject(raw, responseType)
    return parsed?.right()
      ?: PageIndexError.STRUCTURED_CHAT_PARSE_FAILED.asException().left()
  }

  override suspend fun <T> chatList(
    messages: List<LlmMessage>,
    elementType: Class<T>,
    tags: List<String>
  ): Either<PageIndexException, List<T>> {
    calls.add(messages to tags)
    val raw = resolveResponse(tags)
    val parsed = StructuredResponseJson.readStructuredList(raw, elementType)
    return parsed?.right()
      ?: PageIndexError.STRUCTURED_CHAT_PARSE_FAILED.asException().left()
  }

  fun onTag(tag: String, response: String): FakeStructuredChatService {
    responses[tag] = response
    return this
  }

  fun callCount(): Int = calls.size
  fun callCount(tag: String): Int = calls.count { it.second.contains(tag) }

  private fun resolveResponse(tags: List<String>): String {
    val tag = tags.firstOrNull() ?: return defaultResponse
    return responses[tag] ?: defaultResponse
  }
}
