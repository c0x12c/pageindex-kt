package com.c0x12c.pageindex.core.chat

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultStructuredChatServiceTest {

  data class PersonInfo(val name: String, val age: Int)

  data class AnnotatedInfo(
    @field:JsonPropertyDescription("The person's full legal name")
    val name: String,
    @field:JsonPropertyDescription("Age in years")
    val age: Int
  )

  private fun fakeLlm(response: String) = object : LlmClient {
    override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String = response

    override suspend fun <T> chatStructured(
      messages: List<LlmMessage>,
      responseType: Class<T>,
      tags: List<String>
    ): String = response

    override suspend fun <T> chatStructuredList(
      messages: List<LlmMessage>,
      elementType: Class<T>,
      tags: List<String>
    ): String = response
  }

  @Test
  fun `chat - parses valid JSON into data class`() = runTest {
    val service = DefaultStructuredChatService(
      fakeLlm("""{"name": "John", "age": 30}""")
    )

    val result = service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract: John is 30")),
      responseType = PersonInfo::class.java
    )

    assertTrue(result.isRight())
    val person = result.getOrNull()
    assertEquals("John", person?.name)
    assertEquals(30, person?.age)
  }

  @Test
  fun `chat - handles snake_case JSON response`() = runTest {
    val service = DefaultStructuredChatService(
      fakeLlm("""{"name": "Alice", "age": 25}""")
    )

    val result = service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract info")),
      responseType = PersonInfo::class.java
    )

    assertTrue(result.isRight())
    assertEquals("Alice", result.getOrNull()?.name)
  }

  @Test
  fun `chat - returns error when response is markdown fenced rather than raw JSON`() = runTest {
    val service = DefaultStructuredChatService(
      fakeLlm("```json\n{\"name\": \"Bob\", \"age\": 42}\n```")
    )

    val result = service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract info")),
      responseType = PersonInfo::class.java
    )

    assertTrue(result.isLeft())
  }

  @Test
  fun `chat - returns error on unparseable response`() = runTest {
    val service = DefaultStructuredChatService(
      fakeLlm("This is not JSON at all")
    )

    val result = service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract info")),
      responseType = PersonInfo::class.java
    )

    assertTrue(result.isLeft())
  }

  @Test
  fun `chat - injects system message with schema hint`() = runTest {
    var capturedMessages: List<LlmMessage> = emptyList()
    val capturingLlm = object : LlmClient {
      override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
        """{"name": "Test", "age": 1}"""

      override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): String {
        capturedMessages = messages
        return """{"name": "Test", "age": 1}"""
      }
    }

    val service = DefaultStructuredChatService(capturingLlm)
    service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract info")),
      responseType = PersonInfo::class.java
    )

    assertEquals(2, capturedMessages.size)
    assertEquals(LlmRole.SYSTEM, capturedMessages[0].role)
    assertTrue(capturedMessages[0].content.contains("name"))
    assertTrue(capturedMessages[0].content.contains("age"))
    assertEquals(LlmRole.USER, capturedMessages[1].role)
  }

  @Test
  fun `chat - passes tags through to LLM client`() = runTest {
    var capturedTags: List<String> = emptyList()
    val capturingLlm = object : LlmClient {
      override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
        """{"name": "Test", "age": 1}"""

      override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): String {
        capturedTags = tags
        return """{"name": "Test", "age": 1}"""
      }
    }

    val service = DefaultStructuredChatService(capturingLlm)
    service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract")),
      responseType = PersonInfo::class.java,
      tags = listOf("extraction", "test")
    )

    assertEquals(listOf("extraction", "test"), capturedTags)
  }

  @Test
  fun `chat - retries on parse failure and succeeds on second attempt`() = runTest {
    var callCount = 0
    val retryingLlm = object : LlmClient {
      override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
        "not json"

      override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): String {
        callCount++
        return if (callCount == 1) "not json" else """{"name": "Alice", "age": 25}"""
      }
    }

    val service = DefaultStructuredChatService(retryingLlm)
    val result = service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract info")),
      responseType = PersonInfo::class.java
    )

    assertTrue(result.isRight())
    assertEquals("Alice", result.getOrNull()?.name)
    assertEquals(2, callCount)
  }

  @Test
  fun `chat - returns error after fallback retry exhausted`() = runTest {
    var callCount = 0
    val alwaysFailLlm = object : LlmClient {
      override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
        "still not json"

      override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): String {
        callCount++
        return "still not json"
      }
    }

    val service = DefaultStructuredChatService(alwaysFailLlm)
    val result = service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract info")),
      responseType = PersonInfo::class.java
    )

    assertTrue(result.isLeft())
    assertEquals(2, callCount)
  }

  @Test
  fun `chat - includes field descriptions in system prompt`() = runTest {
    var capturedMessages: List<LlmMessage> = emptyList()
    val capturingLlm = object : LlmClient {
      override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
        """{"name": "Test", "age": 1}"""

      override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): String {
        capturedMessages = messages
        return """{"name": "Test", "age": 1}"""
      }
    }

    val service = DefaultStructuredChatService(capturingLlm)
    service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract info")),
      responseType = AnnotatedInfo::class.java
    )

    val systemContent = capturedMessages[0].content
    assertTrue(systemContent.contains("full legal name"))
    assertTrue(systemContent.contains("Age in years"))
  }

  @Test
  fun `chat - passes responseType to chatStructured`() = runTest {
    var capturedType: Class<*>? = null
    val capturingLlm = object : LlmClient {
      override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
        """{"name": "Test", "age": 1}"""

      override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): String {
        capturedType = responseType
        return """{"name": "Test", "age": 1}"""
      }
    }

    val service = DefaultStructuredChatService(capturingLlm)
    service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "Extract")),
      responseType = PersonInfo::class.java
    )

    assertEquals(PersonInfo::class.java, capturedType)
  }
}
