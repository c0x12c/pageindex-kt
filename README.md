# PageIndex JVM

[![Build](https://github.com/c0x12c/pageindex-jvm/actions/workflows/build.yml/badge.svg)](https://github.com/c0x12c/pageindex-jvm/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21%2B-green.svg)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org/)

LLM-powered hierarchical document indexing and retrieval for the JVM. Inspired by [VectifyAI/PageIndex](https://github.com/VectifyAI/PageIndex) — reimplemented in Kotlin with a modular, interface-driven architecture.

PageIndex builds a tree-structured index from documents (like a table of contents), then uses LLM reasoning to navigate it and find relevant sections. No vector database. No chunking. Just structure + reasoning.

## How It Works

```
                    ┌──────────────┐
                    │  PDF / Text  │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  Structure   │    Tries: TOC regex → Headers → LLM detection → Fallback
                    │  Detection   │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  Tree Index  │    Hierarchical tree with titles, page ranges, summaries
                    │              │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  LLM Query   │    Navigate tree → Select sections → Generate answer
                    │              │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │   Answer +   │    Answer with source pages, reasoning, section references
                    │  Source Pages │
                    └──────────────┘
```

**Stage 1: Build Index** — Detect document structure (TOC, headers, or LLM-based) and build a hierarchical tree.

**Stage 2: Retrieve** — Given a query, the LLM navigates the tree to select the most relevant sections.

### Structure Detection Methods

PageIndex tries multiple detection strategies in order:

| Method | How |
|---|---|
| `TOC_WITH_PAGES` | Regex-based TOC detection (dot leaders, dash leaders, tab-separated) |
| `TOC_WITHOUT_PAGES` | LLM extracts TOC that lacks page numbers, then matches titles to pages |
| `HEADER_BASED` | Detects markdown-style headers (`# ## ###`) |
| `LLM_DETECTED` | LLM analyzes page groups to detect structure via map-reduce |
| `FLAT_PAGES` | Fallback: chunks pages by text length |

## Quick Start

### 1. Add Dependency

Add JitPack repository and the dependency:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven("https://jitpack.io")
  }
}

// build.gradle.kts
dependencies {
  implementation("com.github.c0x12c:pageindex-jvm:0.1.0")
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
| `ParsedPageStore` | Persist/load parsed pages (optional) |

## Compared to Vector-Based RAG

| | Vector RAG | PageIndex |
|---|---|---|
| Chunking | Fixed-size or semantic | Natural document sections |
| Index | Embedding vectors | Hierarchical tree |
| Retrieval | Similarity search | LLM reasoning over structure |
| Interpretability | Low (which chunk matched?) | High (section title + page range) |
| Setup | Vector DB + embeddings | Just an LLM |

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
io.pageindex.api/          # Public interfaces, models, and PageIndexException
io.pageindex.core/         # Default implementations (internal)
  model/                   # Internal data models (not part of public API)
  detector/                # Structure detection (regex, headers, LLM)
  retriever/               # Node retrieval (LLM-based, hybrid BM25+embedding)
  llm/                     # LiteLlmClient (multi-provider LLM client)
  prompt/                  # Prompt template system
  verify/                  # TOC verification and fixing
  util/                    # JSON parsing, token counting, case conversion
```

## Requirements

- JDK 21+
- Kotlin 2.0+
- An LLM API (any provider)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to get started.

## Inspired By

This project is a Kotlin/JVM reimplementation of [VectifyAI/PageIndex](https://github.com/VectifyAI/PageIndex), a Python library for vectorless, reasoning-based RAG that achieved 98.7% accuracy on the FinanceBench benchmark.

Key differences from the original:

| | VectifyAI/PageIndex | This Project |
|---|---|---|
| Language | Python | Kotlin/JVM |
| Architecture | Single-file functions | Interface-driven, modular |
| LLM support | LiteLLM (Python) | Built-in `LiteLlmClient` + pluggable `LlmClient` interface |
| Error handling | Exceptions | Arrow `Either<L, R>` |
| Retrieval | External agent loop | Built-in `NodeRetriever` |
| Embeddings | None | Optional `NodeEmbeddingService` |
| Testing | None | JUnit 5 + MockK |

## License

Apache License 2.0 - see [LICENSE](LICENSE)
