/**
 * PageIndex Quick Start Example
 *
 * Shows how to build a document index and query it.
 *
 * Run: copy this file into your project and add the pageindex-kt dependency.
 * Set LLM_API_KEY env variable before running.
 */

import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.core.PageIndex
import com.c0x12c.pageindex.core.llm.LiteLlmClient
import kotlinx.coroutines.runBlocking
import java.util.UUID

fun main() = runBlocking {

    // ── Step 1: Create an LLM client ──────────────────────────────────────

    // Option A: OpenAI
    val llmClient = LiteLlmClient.openai(
        apiKey = System.getenv("LLM_API_KEY") ?: error("Set LLM_API_KEY env variable"),
        model = "gpt-4o"
    )

    // Option B: Anthropic Claude
    // val llmClient = LiteLlmClient.anthropic(
    //   apiKey = System.getenv("ANTHROPIC_API_KEY") ?: error("Set ANTHROPIC_API_KEY"),
    //   model = "claude-sonnet-4-20250514"
    // )

    // Option C: Ollama (local, no API key needed)
    // val llmClient = LiteLlmClient.ollama("llama3")

    // Option D: Auto-detect from env vars (LLM_PROVIDER, LLM_API_KEY, LLM_MODEL)
    // val llmClient = LiteLlmClient.fromEnv()

    // ── Step 2: Create PageIndex ──────────────────────────────────────────

    val pageIndex = PageIndex.create {
        this.llmClient = llmClient
    }

    // ── Step 3: Build an index from document pages ────────────────────────

    val documentId = UUID.randomUUID()
    val projectId = UUID.randomUUID()

    // These would come from your PDF parser (Apache PDFBox, iText, etc.)
    val pages = listOf(
        ParsedPage(1, "Annual Report 2024\nAcme Corporation"),
        ParsedPage(2, "Table of Contents\n1. Executive Summary ..... 3\n2. Financial Results ..... 5\n3. Outlook ..... 8"),
        ParsedPage(3, "1. Executive Summary\nThis year we achieved record revenue of \$50M..."),
        ParsedPage(4, "...continued growth across all business segments."),
        ParsedPage(5, "2. Financial Results\nRevenue: \$50M (+25% YoY)\nNet Income: \$8M"),
        ParsedPage(6, "Operating expenses decreased by 10%..."),
        ParsedPage(7, "Cash flow from operations was \$12M..."),
        ParsedPage(8, "3. Outlook\nWe expect continued growth in 2025...")
    )

    val tree = pageIndex.buildAndSave(documentId, projectId, pages, IndexingConfig())
    tree.fold(
        { error -> println("Index build failed: ${error.message}") },
        { doc -> println("Index built! ${doc.metadata.totalNodes} nodes, method: ${doc.metadata.indexingMethod}") }
    )

    // ── Step 4: Query the document ────────────────────────────────────────

    val result = pageIndex.query(
        documentId,
        listOf(LlmMessage(LlmRole.USER, "What was the company's revenue?"))
    )
    result.fold(
        { error -> println("Query failed: ${error.message}") },
        { answer ->
            println("Answer: ${answer.data}")
            println("Source pages: ${answer.sourcePages}")
            println("Reasoning: ${answer.reasoning}")
        }
    )
}
