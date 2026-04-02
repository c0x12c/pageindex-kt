# PageIndex Benchmark

Benchmark framework for evaluating PageIndex accuracy on financial QA datasets.

## Quick Start

### 1. Get the PDFs

Download SEC filing PDFs from the [FinanceBench repo](https://github.com/patronus-ai/financebench/tree/main/pdfs) and place them in `benchmark/data/pdfs/`:

```
benchmark/data/pdfs/
  3M_2018_10K.pdf
  3M_2022_10K.pdf
  AMCOR_2023_10K.pdf
  APPLE_2022_10K.pdf
  JPMORGAN_2021_10K.pdf
```

The filename must match the `doc_name` field in the JSONL file (e.g. `3M_2018_10K` → `3M_2018_10K.pdf`).

### 2. Set API Key

```bash
export OPENAI_API_KEY=sk-...
```

Optional environment variables:
- `OPENAI_MODEL` — model for indexing and querying (default: `gpt-4o`)
- `JUDGE_MODEL` — model for scoring answers (default: same as `OPENAI_MODEL`)

### 3. Run

```bash
./gradlew benchmark:run
```

Or with custom paths:

```bash
./gradlew benchmark:run --args="path/to/dataset.jsonl path/to/pdfs/"
```

Results are printed to console and saved to `benchmark/output/results.json`.

## Use the Full FinanceBench Dataset

Replace the subset file with the full dataset:

```bash
# Download from FinanceBench repo
curl -o benchmark/data/financebench_full.jsonl \
  https://raw.githubusercontent.com/patronus-ai/financebench/main/data/financebench_open_source.jsonl

# Download all PDFs (368 files)
git clone https://github.com/patronus-ai/financebench.git /tmp/financebench
cp /tmp/financebench/pdfs/*.pdf benchmark/data/pdfs/

# Run with full dataset
./gradlew benchmark:run --args="benchmark/data/financebench_full.jsonl benchmark/data/pdfs/"
```

## Add a New Dataset

1. Create a JSONL file with your questions (any format).
2. Implement `BenchmarkDataset`:

```kotlin
class MyDataset(private val path: String) : BenchmarkDataset {
    override val name = "MyDataset"

    override fun load(): List<BenchmarkQuestion> {
        // Parse your JSONL and return BenchmarkQuestion list
    }

    override fun documentIds(): Set<String> {
        return load().map { it.documentId }.toSet()
    }
}
```

3. Use it in `Main.kt` or create your own entry point.

## Add a New Scorer

Implement `BenchmarkScorer`:

```kotlin
class ExactMatchScorer : BenchmarkScorer {
    override suspend fun score(
        question: String,
        expectedAnswer: String,
        actualAnswer: String,
    ): ScoringResult {
        val match = actualAnswer.contains(expectedAnswer, ignoreCase = true)
        return ScoringResult(
            score = if (match) Score.CORRECT else Score.INCORRECT,
            reasoning = if (match) "Exact match found" else "No match",
        )
    }
}
```

## Use a Different LLM Provider

Change the `BenchmarkRunner` to use a different `LiteLlmClient` factory:

```kotlin
// Anthropic
val llmClient = LiteLlmClient.anthropic(apiKey, "claude-sonnet-4-20250514")

// Ollama (local)
val llmClient = LiteLlmClient.ollama("llama3")

// Any OpenAI-compatible API
val llmClient = LiteLlmClient(
    apiKey = "...",
    model = "...",
    baseUrl = "https://your-api.com/v1",
    provider = LlmProvider.OPENAI_COMPATIBLE,
)
```

## Output Format

`results.json` contains:

```json
{
  "totalQuestions": 5,
  "correct": 4,
  "partiallyCorrect": 1,
  "incorrect": 0,
  "errors": 0,
  "accuracy": 0.8,
  "lenientAccuracy": 1.0,
  "avgQueryTimeMs": 3200,
  "avgIndexTimeMs": 15000,
  "results": [ ... ],
  "config": { ... }
}
```
