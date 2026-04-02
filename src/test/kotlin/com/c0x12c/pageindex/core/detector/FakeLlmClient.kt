package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.model.LlmMessage
import java.util.ArrayDeque

class FakeLlmClient(
  private val responses: MutableMap<String, String> = mutableMapOf(),
  private val defaultResponse: String = "{}"
) : LlmClient {

  private val calls = mutableListOf<Pair<List<LlmMessage>, List<String>>>()
  private val tagQueues = mutableMapOf<String, ArrayDeque<String>>()

  override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String {
    calls.add(messages to tags)
    val tag = tags.firstOrNull() ?: return defaultResponse
    val q = tagQueues[tag]
    if (q != null && q.isNotEmpty()) {
      return q.removeFirst()
    }
    return responses[tag] ?: defaultResponse
  }

  fun onTag(tag: String, response: String): FakeLlmClient {
    responses[tag] = response
    return this
  }

  /**
   * FIFO responses for repeated calls using the same tag (e.g. parallel structure-detection-init).
   */
  fun queueOnTag(tag: String, vararg responses: String): FakeLlmClient {
    val q = tagQueues.getOrPut(tag) { ArrayDeque() }
    responses.forEach { q.addLast(it) }
    return this
  }

  fun callCount(): Int = calls.size
  fun callCount(tag: String): Int = calls.count { it.second.contains(tag) }
  fun allCalls(): List<Pair<List<LlmMessage>, List<String>>> = calls.toList()
}
