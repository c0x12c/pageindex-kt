package com.c0x12c.pageindex.core.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Multi-provider LLM client. Works with OpenAI, Anthropic, and any OpenAI-compatible API
 * (Ollama, Together, vLLM, LM Studio, etc.) using only JDK HttpClient — zero extra dependencies.
 *
 * ```kotlin
 * // OpenAI
 * val client = LiteLlmClient.openai("sk-...", model = "gpt-4o")
 *
 * // Anthropic Claude
 * val client = LiteLlmClient.anthropic("sk-ant-...", model = "claude-sonnet-4-20250514")
 *
 * // Ollama (local)
 * val client = LiteLlmClient.ollama("llama3")
 *
 * // Any OpenAI-compatible endpoint
 * val client = LiteLlmClient(
 *   apiKey = "key",
 *   model = "my-model",
 *   baseUrl = "https://my-provider.com/v1",
 *   provider = LlmProvider.OPENAI_COMPATIBLE
 * )
 *
 * // Use with PageIndex
 * val pageIndex = PageIndex.create {
 *   llmClient = LiteLlmClient.openai("sk-...")
 * }
 * ```
 */
class LiteLlmClient(
    private val apiKey: String = "",
    private val model: String = "gpt-4o",
    private val baseUrl: String? = null,
    private val provider: LlmProvider = LlmProvider.OPENAI,
    private val maxTokens: Int = 4096,
    private val temperature: Double = 0.0,
    private val requestTimeout: Duration = Duration.ofSeconds(120),
) : LlmClient {

    private val log = LoggerFactory.getLogger(LiteLlmClient::class.java)
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private val resolvedBaseUrl: String = (baseUrl ?: when (provider) {
        LlmProvider.OPENAI -> "https://api.openai.com/v1"
        LlmProvider.ANTHROPIC -> "https://api.anthropic.com"
        LlmProvider.OPENAI_COMPATIBLE -> "http://localhost:11434/v1"
    }).trimEnd('/')

    override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String {
        return when (provider) {
            LlmProvider.OPENAI, LlmProvider.OPENAI_COMPATIBLE -> chatOpenAi(messages, jsonMode = false)
            LlmProvider.ANTHROPIC -> chatAnthropic(messages)
        }
    }

    override suspend fun <T> chatStructured(
        messages: List<LlmMessage>,
        responseType: Class<T>,
        tags: List<String>
    ): String {
        return when (provider) {
            LlmProvider.OPENAI -> chatOpenAi(messages, jsonMode = true)
            else -> chat(messages, tags)
        }
    }

    override suspend fun <T> chatStructuredList(
        messages: List<LlmMessage>,
        elementType: Class<T>,
        tags: List<String>
    ): String {
        return when (provider) {
            LlmProvider.OPENAI -> chatOpenAi(messages, jsonMode = true)
            else -> chat(messages, tags)
        }
    }

    private suspend fun chatOpenAi(messages: List<LlmMessage>, jsonMode: Boolean): String {
        val body = mapper.createObjectNode().apply {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            if (jsonMode) {
                putObject("response_format").put("type", "json_object")
            }
            putArray("messages").apply {
                messages.forEach { msg ->
                    addObject().apply {
                        put("role", msg.role.name.lowercase())
                        put("content", msg.content)
                    }
                }
            }
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$resolvedBaseUrl/chat/completions"))
            .header("Content-Type", "application/json")
            .apply {
                if (apiKey.isNotBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build()

        log.debug("OpenAI request to {} with model {}", request.uri(), model)

        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() !in 200..299) {
            throw RuntimeException("OpenAI API error ${response.statusCode()}: ${response.body()}")
        }

        val json = mapper.readTree(response.body())
        return json["choices"][0]["message"]["content"].asText()
    }

    private suspend fun chatAnthropic(messages: List<LlmMessage>): String {
        val systemMessage = messages.firstOrNull { it.role == LlmRole.SYSTEM }?.content
        val nonSystemMessages = messages.filter { it.role != LlmRole.SYSTEM }

        val body = mapper.createObjectNode().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            if (systemMessage != null) {
                put("system", systemMessage)
            }
            putArray("messages").apply {
                nonSystemMessages.forEach { msg ->
                    addObject().apply {
                        put("role", msg.role.name.lowercase())
                        put("content", msg.content)
                    }
                }
            }
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$resolvedBaseUrl/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build()

        log.debug("Anthropic request to {} with model {}", request.uri(), model)

        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Anthropic API error ${response.statusCode()}: ${response.body()}")
        }

        val json = mapper.readTree(response.body())
        return json["content"][0]["text"].asText()
    }

    companion object {
        /** Create a client for OpenAI. */
        fun openai(apiKey: String, model: String = "gpt-4o") = LiteLlmClient(
            apiKey = apiKey,
            model = model,
            provider = LlmProvider.OPENAI
        )

        /** Create a client for Anthropic Claude. */
        fun anthropic(apiKey: String, model: String = "claude-sonnet-4-20250514") = LiteLlmClient(
            apiKey = apiKey,
            model = model,
            provider = LlmProvider.ANTHROPIC
        )

        /** Create a client for Ollama (local). */
        fun ollama(model: String = "llama3", baseUrl: String = "http://localhost:11434/v1") = LiteLlmClient(
            model = model,
            baseUrl = baseUrl,
            provider = LlmProvider.OPENAI_COMPATIBLE
        )

        /** Create a client from environment variables. */
        fun fromEnv(): LiteLlmClient {
            val providerName = System.getenv("LLM_PROVIDER") ?: "openai"
            val provider = when (providerName.lowercase()) {
                "anthropic" -> LlmProvider.ANTHROPIC
                "openai_compatible", "ollama" -> LlmProvider.OPENAI_COMPATIBLE
                else -> LlmProvider.OPENAI
            }
            return LiteLlmClient(
                apiKey = System.getenv("LLM_API_KEY")
                    ?: System.getenv("OPENAI_API_KEY")
                    ?: System.getenv("ANTHROPIC_API_KEY")
                    ?: "",
                model = System.getenv("LLM_MODEL") ?: when (provider) {
                    LlmProvider.OPENAI -> "gpt-4o"
                    LlmProvider.ANTHROPIC -> "claude-sonnet-4-20250514"
                    LlmProvider.OPENAI_COMPATIBLE -> "llama3"
                },
                baseUrl = System.getenv("LLM_BASE_URL"),
                provider = provider
            )
        }
    }
}

/** Supported LLM providers. */
enum class LlmProvider {
    /** OpenAI API (api.openai.com). */
    OPENAI,

    /** Anthropic Messages API (api.anthropic.com). */
    ANTHROPIC,

    /** Any OpenAI-compatible endpoint (Ollama, Together, vLLM, LM Studio, etc.). */
    OPENAI_COMPATIBLE
}
