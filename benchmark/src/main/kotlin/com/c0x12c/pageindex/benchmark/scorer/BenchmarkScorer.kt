package com.c0x12c.pageindex.benchmark.scorer

import com.c0x12c.pageindex.benchmark.model.Score

data class ScoringResult(val score: Score, val reasoning: String)

/**
 * Interface for scoring benchmark answers.
 *
 * Implement this to add a new scoring strategy:
 * 1. Create a class that implements BenchmarkScorer
 * 2. Compare actualAnswer against expectedAnswer
 * 3. Return a ScoringResult with score and reasoning
 *
 * See LlmJudgeScorer for an example.
 */
interface BenchmarkScorer {
    suspend fun score(question: String, expectedAnswer: String, actualAnswer: String): ScoringResult
}
