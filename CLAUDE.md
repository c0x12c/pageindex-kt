# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build              # Full build with tests
./gradlew test               # Run all tests
./gradlew test --tests "com.c0x12c.pageindex.core.SomeTest"  # Run a single test class
./gradlew test --tests "com.c0x12c.pageindex.core.SomeTest.someMethod"  # Run a single test method
./gradlew publishToMavenLocal  # Publish to local Maven repo
```

Requires JDK 21+ and Kotlin 2.0+.

## Architecture

PageIndex is a Kotlin/JVM library for LLM-powered hierarchical document indexing and retrieval. It builds tree-structured indexes from documents and uses LLM reasoning to navigate them — no vector databases or chunking required.

### Two-Stage Pipeline

**Build stage**: Detect document structure → build hierarchical tree → generate summaries → compute embeddings.

**Retrieve stage**: Serialize tree to compact JSON → LLM selects relevant nodes → extract sections with source pages and reasoning.

### Package Layout

- `com.c0x12c.pageindex.api/` — Public interfaces, data models, and `PageIndexException`. All new public interfaces go here.
- `com.c0x12c.pageindex.api.model/` — Public data models (DocumentTree, ParsedPage, IndexingConfig, etc.).
- `com.c0x12c.pageindex.core/` — Entry points: `PageIndexBuilder`, `DefaultPageIndexManager`.
- `com.c0x12c.pageindex.core.indexer/` — Tree building pipeline: `TreeIndexBuilder`, `TreeBuilder`, `NodeSplitter`, `SummaryGenerator`.
- `com.c0x12c.pageindex.core.retriever/` — Node retrieval: `NodeRetriever`, `DefaultNodeRetriever`, `HybridNodeRetriever`, `CompactTreeSerializer`.
- `com.c0x12c.pageindex.core.chat/` — Structured LLM chat: `StructuredChatService`, `DefaultStructuredChatService`.
- `com.c0x12c.pageindex.core.store/` — Persistence: `InMemoryDocumentTreeStore`.
- `com.c0x12c.pageindex.core.embedding/` — Embedding: `NoOpEmbeddingService`.
- `com.c0x12c.pageindex.core.detector/` — Structure detection chain.
- `com.c0x12c.pageindex.core.model/` — Internal data models (not part of public API).
- `com.c0x12c.pageindex.core.llm/` — `LiteLlmClient` multi-provider LLM client.
- `com.c0x12c.pageindex.core.prompt/` — Prompt template rendering.
- `com.c0x12c.pageindex.core.verify/` — TOC verification and fixing.
- `com.c0x12c.pageindex.core.util/` — JSON parsing, schema hints, token counting.

### Public Interfaces (api/)

| Interface | Purpose |
|---|---|
| `PageIndexManager` | Main entry point: `buildAndSave`, `search`, `query` |
| `LlmClient` | Chat interface — implement for any LLM provider |
| `StructureDetector` | Detects document structure (TOC, headers, etc.) |
| `NodeEmbeddingService` | Generates embeddings for tree nodes |
| `DocumentTreeStore` | Persistence layer for `DocumentTree` |
| `PromptProvider` | Template-based prompt management |

### Internal Interfaces

| Interface | Package | Purpose |
|---|---|---|
| `TreeIndexBuilder` | `core.indexer` | Builds hierarchical index from `ParsedPage` list |
| `NodeRetriever` | `core.retriever` | LLM-based node selection from tree |
| `StructuredChatService` | `core.chat` | Wraps LlmClient to return parsed JSON objects |

### Structure Detection Chain

`ChainedStructureDetector` tries detectors in order until one succeeds:
1. `RegexTocDetector` — TOC with page number patterns (dots/dashes/tabs)
2. `TocNoPageNumbersDetector` — TOC without page numbers, uses LLM to match
3. `MarkdownHeaderDetector` — Markdown `#`/`##`/`###` headers
4. `LlmStructureDetector` — LLM analyzes page groups via map-reduce
5. `FlatPagesFallback` — Chunks pages by text length as last resort

### Key Data Models

- `DocumentTree` — Root container with metadata, nodes, and embeddings
- `TreeNode` — Hierarchical node with title, page range, summary, children, text
- `ParsedPage` — Input: page number + text content
- `IndexingConfig` — Tunable parameters (token limits, concurrency, thresholds)

### Error Handling Convention

Uses Arrow's `Either<Exception, T>` for all async operations — no thrown exceptions in business logic. `PageIndexException` carries HTTP status codes (400/404/500) and typed `PageIndexError` values.

### Testing

Tests live in `src/test/kotlin/`. Test fixtures include `FakeLlmClient`, `FakeStructuredChatService`, `FixedDetector`, `NullDetector`, and `TrackingDetector`. Uses JUnit 5 and MockK.

### Dependencies

- **Arrow** — `Either<L, R>` for functional error handling
- **Kotlin Coroutines** — Async operations with concurrency controls
- **Jackson** — JSON serialization
- **SLF4J** — Logging
