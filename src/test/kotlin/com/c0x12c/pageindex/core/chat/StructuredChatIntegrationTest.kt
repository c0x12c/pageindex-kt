package com.c0x12c.pageindex.core.chat

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("integration")
class StructuredChatIntegrationTest {

  data class PersonExtraction(
    @field:JsonPropertyDescription("The person's full name")
    val name: String,
    @field:JsonPropertyDescription("Age in years as a whole number")
    val age: Int,
    @field:JsonPropertyDescription("The person's job title or occupation")
    val occupation: String
  )

  data class SentimentAnalysis(
    @field:JsonPropertyDescription("Overall sentiment: positive, negative, or neutral")
    val sentiment: String,
    @field:JsonPropertyDescription("Confidence score from 0.0 to 1.0")
    val confidence: Double,
    @field:JsonPropertyDescription("Key phrases that indicate the sentiment")
    val keyPhrases: List<String>
  )

  data class BooleanExtraction(
    @field:JsonPropertyDescription("Whether the text mentions a deadline")
    val hasDeadline: Boolean,
    @field:JsonPropertyDescription("The deadline date if mentioned, otherwise empty string")
    val deadlineDate: String
  )

  private fun echoSchemaLlm(): LlmClient {
    val handler: (List<LlmMessage>, List<String>) -> String = { messages, _ ->
      val systemMessage = messages.first { it.role == LlmRole.SYSTEM }
      val userMessage = messages.first { it.role == LlmRole.USER }
      when {
        userMessage.content.contains("John Smith") ->
          """{"name": "John Smith", "age": 35, "occupation": "Software Engineer"}"""
        userMessage.content.contains("love this product") ->
          """{"sentiment": "positive", "confidence": 0.92, "key_phrases": ["love", "amazing quality"]}"""
        userMessage.content.contains("no rush") ->
          """{"has_deadline": false, "deadline_date": ""}"""
        userMessage.content.contains("deadline") ->
          """{"has_deadline": true, "deadline_date": "2026-04-01"}"""
        else -> systemMessage.content.substringAfter("\n").trim()
      }
    }
    return object : LlmClient {
      override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
        handler(messages, tags)

      override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): String = handler(messages, tags)
    }
  }

  @Test
  fun `person extraction - parses all fields correctly`() = runTest {
    val service = DefaultStructuredChatService(echoSchemaLlm())

    val result = service.chat(
      messages = listOf(
        LlmMessage(LlmRole.USER, "Extract person info: John Smith is a 35 year old Software Engineer")
      ),
      responseType = PersonExtraction::class.java,
      tags = listOf("extraction")
    )

    assertTrue(result.isRight())
    val person = result.getOrNull()
    assertNotNull(person)
    assertEquals("John Smith", person?.name)
    assertEquals(35, person?.age)
    assertEquals("Software Engineer", person?.occupation)
  }

  @Test
  fun `sentiment analysis - handles nested list field`() = runTest {
    val service = DefaultStructuredChatService(echoSchemaLlm())

    val result = service.chat(
      messages = listOf(
        LlmMessage(LlmRole.USER, "Analyze sentiment: I love this product, amazing quality!")
      ),
      responseType = SentimentAnalysis::class.java,
      tags = listOf("sentiment")
    )

    assertTrue(result.isRight())
    val analysis = result.getOrNull()
    assertNotNull(analysis)
    assertEquals("positive", analysis?.sentiment)
    assertEquals(0.92, analysis?.confidence)
    assertEquals(listOf("love", "amazing quality"), analysis?.keyPhrases)
  }

  @Test
  fun `boolean extraction - deadline present`() = runTest {
    val service = DefaultStructuredChatService(echoSchemaLlm())

    val result = service.chat(
      messages = listOf(
        LlmMessage(LlmRole.USER, "Check for deadline: The report is due by April 1st 2026")
      ),
      responseType = BooleanExtraction::class.java,
      tags = listOf("extraction")
    )

    assertTrue(result.isRight())
    val extraction = result.getOrNull()
    assertEquals(true, extraction?.hasDeadline)
    assertEquals("2026-04-01", extraction?.deadlineDate)
  }

  @Test
  fun `boolean extraction - no deadline`() = runTest {
    val service = DefaultStructuredChatService(echoSchemaLlm())

    val result = service.chat(
      messages = listOf(
        LlmMessage(LlmRole.USER, "Check for deadline: Take your time, no rush on this one")
      ),
      responseType = BooleanExtraction::class.java,
      tags = listOf("extraction")
    )

    assertTrue(result.isRight())
    val extraction = result.getOrNull()
    assertEquals(false, extraction?.hasDeadline)
    assertEquals("", extraction?.deadlineDate)
  }

  @Test
  fun `multi-turn conversation - messages are passed in order`() = runTest {
    var capturedMessages: List<LlmMessage> = emptyList()
    val capturingLlm = object : LlmClient {
      override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
        """{"name": "Alice", "age": 28, "occupation": "Designer"}"""

      override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): String {
        capturedMessages = messages
        return """{"name": "Alice", "age": 28, "occupation": "Designer"}"""
      }
    }

    val service = DefaultStructuredChatService(capturingLlm)
    service.chat(
      messages = listOf(
        LlmMessage(LlmRole.USER, "I met someone named Alice today"),
        LlmMessage(LlmRole.ASSISTANT, "Tell me more about Alice"),
        LlmMessage(LlmRole.USER, "She's a 28 year old Designer")
      ),
      responseType = PersonExtraction::class.java
    )

    assertEquals(4, capturedMessages.size)
    assertEquals(LlmRole.SYSTEM, capturedMessages[0].role)
    assertEquals(LlmRole.USER, capturedMessages[1].role)
    assertEquals(LlmRole.ASSISTANT, capturedMessages[2].role)
    assertEquals(LlmRole.USER, capturedMessages[3].role)
  }

  @Test
  fun `system prompt contains field descriptions from annotations`() = runTest {
    var capturedSystem = ""
    val capturingLlm = object : LlmClient {
      override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String =
        """{"name": "Test", "age": 1, "occupation": "Test"}"""

      override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
      ): String {
        capturedSystem = messages.first { it.role == LlmRole.SYSTEM }.content
        return """{"name": "Test", "age": 1, "occupation": "Test"}"""
      }
    }

    val service = DefaultStructuredChatService(capturingLlm)
    service.chat(
      messages = listOf(LlmMessage(LlmRole.USER, "test")),
      responseType = PersonExtraction::class.java
    )

    assertTrue(capturedSystem.contains("full name"), "Should contain name description")
    assertTrue(capturedSystem.contains("whole number"), "Should contain age description")
    assertTrue(capturedSystem.contains("job title"), "Should contain occupation description")
  }
}
