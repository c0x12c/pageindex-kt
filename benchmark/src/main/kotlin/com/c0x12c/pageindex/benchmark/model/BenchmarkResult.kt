package com.c0x12c.pageindex.benchmark.model

enum class Score { CORRECT, PARTIALLY_CORRECT, INCORRECT, ERROR }

data class QuestionResult(
    val questionId: String,
    val question: String,
    val expectedAnswer: String,
    val actualAnswer: String,
    val score: Score,
    val reasoning: String,
    val sourcePages: List<Int>,
    val expectedPages: List<Int>,
    val indexingTimeMs: Long,
    val queryTimeMs: Long,
)

data class BenchmarkSummary(
    val totalQuestions: Int,
    val correct: Int,
    val partiallyCorrect: Int,
    val incorrect: Int,
    val errors: Int,
    val accuracy: Double,
    val lenientAccuracy: Double,
    val avgQueryTimeMs: Long,
    val avgIndexTimeMs: Long,
    val results: List<QuestionResult>,
    val config: Map<String, String>,
)
