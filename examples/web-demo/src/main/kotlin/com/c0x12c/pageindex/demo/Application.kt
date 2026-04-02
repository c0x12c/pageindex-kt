package com.c0x12c.pageindex.demo

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.TreeNode
import com.c0x12c.pageindex.core.store.InMemoryDocumentTreeStore
import com.c0x12c.pageindex.core.PageIndex
import com.c0x12c.pageindex.core.llm.LiteLlmClient
import com.c0x12c.pageindex.core.llm.LlmProvider
import org.slf4j.LoggerFactory
import java.util.*

/**
 * PageIndex Web Demo
 *
 * Run:
 *   cd examples/web-demo
 *   LLM_API_KEY=sk-... ../../gradlew run
 *
 * Then open http://localhost:8080
 */
fun main() {
    embeddedServer(Netty, port = 8080) {
        configureApp()
    }.start(wait = true)
}

private val logger = LoggerFactory.getLogger("com.c0x12c.pageindex.demo")
private val store = InMemoryDocumentTreeStore()

fun Application.configureApp() {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Request failed", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Unknown error")
            )
        }
    }

    routing {
        route("/api") {
            post("/build") {
                val request = call.receive<BuildRequest>()

                if (request.markdown.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Markdown text is required"))
                    return@post
                }

                val llmClient = createLlmClient(request.settings)
                val pageIndex = PageIndex.create {
                    this.llmClient = llmClient
                    this.documentTreeStore = store
                }

                val pages = markdownToPages(request.markdown)
                val documentId = UUID.randomUUID()
                val projectId = UUID.randomUUID()

                logger.info("Building index: {} pages, documentId={}", pages.size, documentId)

                val result = pageIndex.buildAndSave(documentId, projectId, pages, IndexingConfig())
                result.fold(
                    { error ->
                        logger.error("Build failed: {}", error.message)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error.message ?: "Build failed"))
                    },
                    { tree ->
                        logger.info("Build complete: {} nodes, method={}", tree.metadata.totalNodes, tree.metadata.indexingMethod)
                        call.respond(BuildResponse(
                            documentId = tree.documentId.toString(),
                            totalNodes = tree.metadata.totalNodes,
                            totalPages = tree.metadata.totalPages,
                            maxDepth = tree.metadata.maxDepth,
                            indexingMethod = tree.metadata.indexingMethod.name,
                            tree = tree.rootNodes
                        ))
                    }
                )
            }

            post("/query") {
                val request = call.receive<QueryRequest>()

                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query is required"))
                    return@post
                }

                val llmClient = createLlmClient(request.settings)
                val pageIndex = PageIndex.create {
                    this.llmClient = llmClient
                    this.documentTreeStore = store
                }

                val documentId = UUID.fromString(request.documentId)
                logger.info("Querying document {}: {}", documentId, request.query)

                val result = pageIndex.query(
                    documentId,
                    listOf(LlmMessage(LlmRole.USER, request.query))
                )
                result.fold(
                    { error ->
                        logger.error("Query failed: {}", error.message)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error.message ?: "Query failed"))
                    },
                    { answer ->
                        call.respond(QueryResponse(
                            answer = answer.data,
                            reasoning = answer.reasoning,
                            sourcePages = answer.sourcePages,
                            selectedNodeIds = answer.selectedNodeIds
                        ))
                    }
                )
            }
        }

        // Serve static files (index.html) — must come after /api routes
        staticResources("/", "static")
    }
}

fun createLlmClient(settings: LlmSettings?): LiteLlmClient {
    val providerName = settings?.provider ?: System.getenv("LLM_PROVIDER") ?: "openai"
    val provider = when (providerName.lowercase()) {
        "anthropic" -> LlmProvider.ANTHROPIC
        "openai_compatible", "ollama" -> LlmProvider.OPENAI_COMPATIBLE
        else -> LlmProvider.OPENAI
    }

    val apiKey = settings?.apiKey
        ?: System.getenv("LLM_API_KEY")
        ?: System.getenv("OPENAI_API_KEY")
        ?: System.getenv("ANTHROPIC_API_KEY")
        ?: ""

    val model = settings?.model ?: System.getenv("LLM_MODEL") ?: when (provider) {
        LlmProvider.OPENAI -> "gpt-4o"
        LlmProvider.ANTHROPIC -> "claude-sonnet-4-20250514"
        LlmProvider.OPENAI_COMPATIBLE -> "llama3"
    }

    return LiteLlmClient(
        apiKey = apiKey,
        model = model,
        provider = provider,
        baseUrl = settings?.baseUrl ?: System.getenv("LLM_BASE_URL")
    )
}

/**
 * Split markdown into pages. Groups paragraphs to ~1500 chars each.
 * Keeps headers at the start of a page when possible.
 */
fun markdownToPages(text: String, charsPerPage: Int = 1500): List<ParsedPage> {
    val paragraphs = text.split(Regex("\n{2,}"))
    val pages = mutableListOf<ParsedPage>()
    val current = StringBuilder()
    var pageNum = 1

    for (paragraph in paragraphs) {
        val isHeader = paragraph.trimStart().startsWith("#")
        val wouldOverflow = current.length + paragraph.length > charsPerPage && current.isNotEmpty()

        // Start new page if overflow, or if this is a header and current page has content
        if (wouldOverflow || (isHeader && current.length > charsPerPage / 3)) {
            pages.add(ParsedPage(pageNum++, current.toString().trim()))
            current.clear()
        }

        if (current.isNotEmpty()) current.append("\n\n")
        current.append(paragraph)
    }

    if (current.isNotEmpty()) {
        pages.add(ParsedPage(pageNum, current.toString().trim()))
    }

    return pages.ifEmpty { listOf(ParsedPage(1, text)) }
}

// --- Request / Response DTOs ---

data class LlmSettings(
    val provider: String? = null,
    val model: String? = null,
    @JsonProperty("apiKey") val apiKey: String? = null,
    val baseUrl: String? = null
)

data class BuildRequest(
    val markdown: String = "",
    val settings: LlmSettings? = null
)

data class BuildResponse(
    val documentId: String,
    val totalNodes: Int,
    val totalPages: Int,
    val maxDepth: Int,
    val indexingMethod: String,
    val tree: List<TreeNode>
)

data class QueryRequest(
    val documentId: String = "",
    val query: String = "",
    val settings: LlmSettings? = null
)

data class QueryResponse(
    val answer: String,
    val reasoning: String,
    val sourcePages: List<Int>,
    val selectedNodeIds: List<String>
)

data class ErrorResponse(val error: String)
