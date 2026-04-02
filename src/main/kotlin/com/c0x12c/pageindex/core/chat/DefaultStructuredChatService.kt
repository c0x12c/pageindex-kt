package com.c0x12c.pageindex.core.chat

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.c0x12c.pageindex.api.PageIndexException
import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.api.model.PageIndexError
import com.c0x12c.pageindex.core.util.SchemaHintGenerator
import com.c0x12c.pageindex.core.util.StructuredResponseJson
import org.slf4j.LoggerFactory

class DefaultStructuredChatService(
  private val llmClient: LlmClient
) : StructuredChatService {

  private val logger = LoggerFactory.getLogger(DefaultStructuredChatService::class.java)

  override suspend fun <T> chat(
    messages: List<LlmMessage>,
    responseType: Class<T>,
    tags: List<String>
  ): Either<PageIndexException, T> {
    val schemaHint = SchemaHintGenerator.generate(responseType)
    val systemMessage = LlmMessage(
      LlmRole.SYSTEM,
      "Respond ONLY with a valid JSON object matching this structure. " +
        "No markdown, no explanation, no code fences.\n$schemaHint"
    )
    val allMessages = listOf(systemMessage) + messages

    val rawResponse = llmClient.chatStructured(allMessages, responseType, tags)
    val parsed = StructuredResponseJson.readObject(rawResponse, responseType)
    if (parsed != null) return parsed.right()

    logger.warn("Structured chat deserialize failed for ${responseType.simpleName} despite responseFormat. Attempting fallback retry. Response prefix: ${rawResponse.take(200)}")
    val retryMessages = allMessages +
      LlmMessage(LlmRole.ASSISTANT, rawResponse) +
      LlmMessage(
        LlmRole.USER,
        CORRECTIVE_OBJECT_PROMPT + "\n$schemaHint"
      )
    val retryResponse = llmClient.chatStructured(retryMessages, responseType, tags)
    val retryParsed = StructuredResponseJson.readObject(retryResponse, responseType)
    if (retryParsed != null) return retryParsed.right()

    return PageIndexError.STRUCTURED_CHAT_PARSE_FAILED.asException().left()
  }

  override suspend fun <T> chatList(
    messages: List<LlmMessage>,
    elementType: Class<T>,
    tags: List<String>
  ): Either<PageIndexException, List<T>> {
    val schemaHint = SchemaHintGenerator.generate(elementType)
    val systemMessage = LlmMessage(
      LlmRole.SYSTEM,
      "Respond ONLY with a valid JSON object with a single property \"" +
        StructuredResponseJson.STRUCTURED_LIST_ITEMS_PROPERTY +
        "\" whose value is a JSON array of objects matching this structure. " +
        "No markdown, no explanation, no code fences.\n$schemaHint"
    )
    val allMessages = listOf(systemMessage) + messages

    val rawResponse = llmClient.chatStructuredList(allMessages, elementType, tags)
    val parsed = StructuredResponseJson.readStructuredList(rawResponse, elementType)
    if (parsed != null) return parsed.right()

    logger.warn("Structured chat list deserialize failed for ${elementType.simpleName} despite responseFormat. Attempting fallback retry. Response prefix: ${rawResponse.take(200)}")
    val retryMessages = allMessages +
      LlmMessage(LlmRole.ASSISTANT, rawResponse) +
      LlmMessage(
        LlmRole.USER,
        CORRECTIVE_ARRAY_PROMPT + "\n$schemaHint"
      )
    val retryResponse = llmClient.chatStructuredList(retryMessages, elementType, tags)
    val retryParsed = StructuredResponseJson.readStructuredList(retryResponse, elementType)
    if (retryParsed != null) return retryParsed.right()

    return PageIndexError.STRUCTURED_CHAT_PARSE_FAILED.asException().left()
  }

  companion object {
    private const val CORRECTIVE_OBJECT_PROMPT =
      "Your JSON response could not be deserialized. It must be a single raw JSON object " +
        "with no markdown, no code fences, and no text before or after the object."

    private const val CORRECTIVE_ARRAY_PROMPT =
      "Your JSON response could not be deserialized. It must be a JSON object " +
        "with a required \"items\" array property (no markdown or code fences)."
  }
}
