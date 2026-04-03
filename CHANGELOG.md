# Changelog

## 0.1.1 (2026-04-03)

- Modify release workflow for Maven Central publishing.

## 0.1.0 (2025-01-01)

Initial release.

- Hierarchical document indexing with 5 detection strategies (TOC regex, TOC without pages, markdown headers, LLM-detected, flat pages fallback)
- LLM-based tree navigation and retrieval
- Hybrid retrieval with BM25 + embeddings
- TOC verification and auto-fixing
- Batch summary generation with configurable concurrency
- Pluggable interfaces for LLM, storage, embeddings, and structure detection
