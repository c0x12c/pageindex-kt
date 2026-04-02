# PageIndex KT

[![Build](https://github.com/c0x12c/pageindex-kt/actions/workflows/build.yml/badge.svg)](https://github.com/c0x12c/pageindex-kt/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21%2B-green.svg)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org/)

LLM-powered hierarchical document indexing and retrieval for the JVM. Built with Kotlin, modular and interface-driven. Originally inspired by [VectifyAI/PageIndex](https://github.com/VectifyAI/PageIndex).

PageIndex builds a tree-structured index from documents (like a table of contents), then uses LLM reasoning to navigate it and find relevant sections. No vector database. No chunking. Just structure + reasoning.

## How It Works

```
                    ┌──────────────┐
                    │  PDF / Text  │
                    └──────┬───────┘
                           │
              ┌────────────▼────────────┐
              │     Regex Detection     │  TOC patterns, markdown headers
              │  (fast, no LLM calls)   │
              └────────────┬────────────┘
                           │ not found?
              ┌────────────▼────────────┐
              │   LLM-Assisted Detection│  Regex discovery, TOC matching,
              │  (smart, cached)        │  map-reduce structure analysis
              └────────────┬────────────┘
                           │ still not found?
              ┌────────────▼────────────┐
              │        Fallback         │  Flat page chunking
              └────────────┬────────────┘
                           │
              ┌────────────▼────────────┐
              │       Tree Index        │  Hierarchical tree with titles,
              │                         │  page ranges, summaries
              └────────────┬────────────┘
                           │
              ┌────────────▼────────────┐
              │       Retrieval         │  LLM reasoning or hybrid
              │                         │  BM25 + embedding
              └────────────┬────────────┘
                           │
              ┌────────────▼────────────┐
              │    Answer + Sources     │  Answer with source pages,
              │                         │  reasoning, section references
              └────────────────────────┘
```

### Pipeline

**Stage 1: Detect Structure** — A chain of 6 detectors runs in order. The first one that succeeds wins. Fast regex methods run first. LLM-based methods run only if needed.

**Stage 2: Build Tree** — Detected sections become a hierarchical tree. Oversized nodes are split at paragraph boundaries. Summaries are generated bottom-up (leaves first, then parents).

**Stage 3: Retrieve** — Given a query, either the LLM navigates the tree to pick relevant sections, or a hybrid BM25+embedding scorer ranks nodes without an LLM call.

### Structure Detection Chain

PageIndex tries 6 detection strategies in order. First match wins:

| # | Method | Type | How |
|---|---|---|---|
| 1 | `TOC_WITH_PAGES` | Regex | Dot leaders, dash leaders, tab-separated page numbers. Handles unicode spaces and physical/logical page offset. |
| 2 | `HEADER_BASED` | Regex | ATX headers (`#`), setext underlines, bold-line headers, numbered outlines. Code-block aware. |
| 3 | `REGEX_DISCOVERED` | LLM + Regex | LLM analyzes page samples to discover header patterns. Patterns are validated, then applied deterministically. Results cached by content fingerprint. |
| 4 | `TOC_WITHOUT_PAGES` | LLM | LLM extracts TOC entries that lack page numbers, then matches titles to pages. |
| 5 | `LLM_DETECTED` | LLM | LLM analyzes page groups via map-reduce to build a hierarchical outline. |
| 6 | `FLAT_PAGES` | Fallback | Chunks pages by text length. Always succeeds. |

**Why this order?** Regex detectors are fast and free. LLM detectors are smart but cost API calls. The chain tries the cheapest option first and only escalates when needed.

### LLM Regex Discovery

This is a unique feature not found in the original PageIndex. When regex and header detectors fail, instead of jumping straight to full LLM analysis, the system:

1. Takes a sample of pages and asks the LLM to find header patterns
2. Validates each pattern: must compile, must have exactly one capturing group, must not match >50% of lines
3. Applies validated patterns deterministically across all pages
4. Caches patterns by SHA-256 fingerprint of the content — same document template never triggers a second LLM call

This gives you the accuracy of LLM analysis with the speed of regex matching. Implement `RegexPatternCache` to persist patterns across runs (e.g., in Redis or a database).

### Hybrid Retrieval

Two retrieval strategies are built in:

| Strategy | How | Best for |
|---|---|---|
| `DefaultNodeRetriever` | LLM reads the tree structure and picks relevant nodes | Highest accuracy, reasoning visible |
| `HybridNodeRetriever` | BM25 text scoring + embedding cosine similarity, merged with Reciprocal Rank Fusion | Fast, no LLM call per query, works without embeddings |

The hybrid retriever degrades gracefully — if no embeddings are available, it falls back to BM25 only. No tuning needed: RRF merges both signals without weight parameters.

### Tree Building

The indexing pipeline has 4 stages:

1. **Detect** — Run the detection chain to find document sections
2. **Build** — Convert flat TOC entries into a hierarchical tree. Front matter (cover pages, preamble) gets a synthetic node so no content is lost
3. **Split** — Break oversized nodes at paragraph boundaries (`\n\n`), not at arbitrary character limits
4. **Summarize** — Generate summaries bottom-up: leaf nodes first (from text), then parents (from child summaries). Batched LLM calls with concurrency control

## Quick Start

### 1. Add Dependency

Add the dependency from Maven Central:

```kotlin
// build.gradle.kts
dependencies {
  implementation("com.c0x12c:pageindex-kt:0.1.0")
}
```

### 2. Create an LLM Client

Use the built-in `LiteLlmClient` for OpenAI, Anthropic, Ollama, or any OpenAI-compatible API:

```kotlin
// OpenAI
val llmClient = LiteLlmClient.openai("sk-...", model = "gpt-4o")

// Anthropic Claude
val llmClient = LiteLlmClient.anthropic("sk-ant-...", model = "claude-sonnet-4-20250514")

// Ollama (local, no API key)
val llmClient = LiteLlmClient.ollama("llama3")

// Any OpenAI-compatible endpoint
val llmClient = LiteLlmClient(
  apiKey = "key",
  model = "my-model",
  baseUrl = "https://my-provider.com/v1",
  provider = LlmProvider.OPENAI_COMPATIBLE
)

// Auto-detect from env vars (LLM_PROVIDER, LLM_API_KEY, LLM_MODEL)
val llmClient = LiteLlmClient.fromEnv()
```

Or implement `LlmClient` yourself for full control:

```kotlin
class MyLlmClient(private val apiKey: String) : LlmClient {
  override suspend fun chat(messages: List<LlmMessage>, tags: List<String>): String {
    // Call any LLM provider
    return callYourLlm(messages)
  }
}
```

### 3. Build and Query

```kotlin
// Create PageIndex
val pageIndex = PageIndex.create {
  llmClient = LiteLlmClient.openai("sk-...")
}

// Build index from parsed pages
val pages = listOf(
  ParsedPage(1, "Cover page text..."),
  ParsedPage(2, "Table of Contents\n1. Introduction ..... 3\n2. Methods ..... 5"),
  ParsedPage(3, "1. Introduction\nThis report covers..."),
)
val tree = pageIndex.buildAndSave(documentId, projectId, pages, IndexingConfig())

// Query the document
val result = pageIndex.query(
  documentId,
  listOf(LlmMessage(LlmRole.USER, "What methods were used?"))
)
result.fold(
  { error -> println("Query failed: ${error.message}") },
  { answer ->
    println("Answer: ${answer.data}")
    println("Source pages: ${answer.sourcePages}")
    println("Reasoning: ${answer.reasoning}")
  }
)
```

### Customize

Override any component in the builder:

```kotlin
val pageIndex = PageIndex.create {
  llmClient = MyLlmClient(apiKey)
  embeddingService = MyEmbeddingService(apiKey)  // default: NoOpEmbeddingService
  documentTreeStore = MyDatabaseStore()          // default: InMemoryDocumentTreeStore
  structureDetector = MyDetector()               // default: full detection chain
  regexPatternCache = MyRedisCache()             // default: none (no caching)
  maxNodes = 10                                  // default: 5
}
```

Or use the Java-style builder:

```kotlin
val pageIndex = PageIndex.builder()
  .llmClient(MyLlmClient(apiKey))
  .embeddingService(MyEmbeddingService(apiKey))
  .maxNodes(10)
  .build()
```

See [`examples/QuickStartExample.kt`](examples/QuickStartExample.kt) for a complete runnable example.

## Try the Web Demo

A web UI for testing PageIndex with markdown documents:

```bash
cd examples/web-demo
LLM_API_KEY=sk-... ../../gradlew run
# Open http://localhost:8080
```

Paste markdown, build a tree index, then query it. Supports OpenAI, Anthropic, and Ollama — configure in the UI or via env vars (`LLM_PROVIDER`, `LLM_API_KEY`, `LLM_MODEL`).

## Interfaces You Implement

| Interface | Purpose |
|---|---|
| `LlmClient` | Chat with any LLM (OpenAI, Claude, Gemini, local) |
| `NodeEmbeddingService` | Generate text embeddings (for hybrid retrieval) |
| `DocumentTreeStore` | Persist/load document trees (database, file, memory) |
| `StructureDetector` | Custom document structure detection (optional) |
| `RegexPatternCache` | Cache LLM-discovered regex patterns across documents (optional) |

## Compared to Vector-Based RAG

| | Vector RAG | PageIndex |
|---|---|---|
| Chunking | Fixed-size or semantic | Natural document sections |
| Index | Embedding vectors | Hierarchical tree |
| Retrieval | Similarity search | LLM reasoning over structure |
| Interpretability | Low (which chunk matched?) | High (section title + page range) |
| Setup | Vector DB + embeddings | Just an LLM |
| Error handling | Varies | Typed errors with `Either<L, R>` |
| Hybrid option | Separate system | Built-in BM25 + embedding fusion |

<details>
<summary><b>Tree Index Example Output</b></summary>

When PageIndex builds an index, the tree looks like this:

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "rootNodes": [
    {
      "nodeId": "node-1",
      "title": "1. Executive Summary",
      "startPage": 3,
      "endPage": 4,
      "summary": "Overview of company performance and key achievements",
      "children": []
    },
    {
      "nodeId": "node-2",
      "title": "2. Financial Results",
      "startPage": 5,
      "endPage": 7,
      "summary": "Revenue, expenses, and cash flow analysis",
      "children": [
        {
          "nodeId": "node-2-1",
          "title": "2.1 Revenue",
          "startPage": 5,
          "endPage": 5
        },
        {
          "nodeId": "node-2-2",
          "title": "2.2 Operating Expenses",
          "startPage": 6,
          "endPage": 7
        }
      ]
    }
  ],
  "metadata": {
    "totalNodes": 5,
    "totalPages": 8,
    "maxDepth": 2,
    "indexingMethod": "TOC_WITH_PAGES"
  }
}
```

</details>

## Architecture

```
com.c0x12c.pageindex.api/          # Public interfaces, models, and PageIndexException
com.c0x12c.pageindex.core/         # Default implementations (internal)
  cache/                   # RegexPatternCache implementations
  chat/                    # Structured LLM chat (JSON-parsed responses)
  detector/                # Structure detection (regex, headers, LLM, regex discovery)
  embedding/               # Embedding service implementations
  indexer/                 # Tree building pipeline
  llm/                     # LiteLlmClient (multi-provider LLM client)
  model/                   # Internal data models (not part of public API)
  prompt/                  # Prompt template system
  retriever/               # Node retrieval (LLM-based, hybrid BM25+embedding)
  store/                   # Persistence (InMemoryDocumentTreeStore)
  util/                    # JSON parsing, token counting, case conversion
  verify/                  # TOC verification and fixing
```

## Requirements

- JDK 21+
- Kotlin 2.0+
- An LLM API (any provider)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to get started.

## Origin

Originally inspired by [VectifyAI/PageIndex](https://github.com/VectifyAI/PageIndex), a Python library for vectorless, reasoning-based RAG. This project started from the same idea but has since grown into a standalone library with its own architecture and features beyond the original.

**What we kept:** The core concept — build a tree index from document structure, then use LLM reasoning to navigate it.

**What we added:**

- **LLM regex discovery** — LLM finds header patterns, then regex applies them. Best of both worlds: LLM accuracy + regex speed. Patterns are cached by content fingerprint so the same document template never costs a second LLM call.
- **Hybrid retrieval** — BM25 + embedding scoring merged with Reciprocal Rank Fusion. No weight tuning needed. Works even without embeddings.
- **6-method detection chain** — Regex TOC, markdown headers, LLM regex discovery, TOC without pages, full LLM analysis, flat fallback. Cheapest method first.
- **Paragraph-aware splitting** — Oversized nodes split at `\n\n` boundaries, not arbitrary character limits.
- **Bottom-up summarization** — Leaves summarized from text, parents from child summaries. Batched LLM calls.
- **Typed error handling** — Arrow `Either<PageIndexException, T>` throughout. No thrown exceptions in business logic. Errors carry HTTP status codes.
- **Pluggable everything** — 8 interfaces you can swap: LLM client, embeddings, storage, detection, retrieval, prompts, chat service, regex cache.

| | VectifyAI/PageIndex | This Project |
|---|---|---|
| Language | Python | Kotlin/JVM |
| Architecture | Single-file functions | Interface-driven, modular |
| LLM support | LiteLLM (Python) | Built-in `LiteLlmClient` + pluggable `LlmClient` interface |
| Error handling | Exceptions | Arrow `Either<L, R>` |
| Structure detection | 4 fixed strategies | 6-method chain with LLM regex discovery + caching |
| Retrieval | External agent loop | Built-in `NodeRetriever` + hybrid BM25+embedding |
| Embeddings | None | Optional `NodeEmbeddingService` with mean-pooling |
| Regex caching | None | Content-fingerprinted `RegexPatternCache` |
| Tree building | Basic | Paragraph-aware splitting, front-matter detection, batched summarization |
| Extensibility | Limited | 8 pluggable interfaces |
| Concurrency | Basic asyncio | Kotlin coroutines with semaphore-based throttling |
| Testing | None | JUnit 5 + MockK with test fixtures |

## License

Apache License 2.0 - see [LICENSE](LICENSE)
