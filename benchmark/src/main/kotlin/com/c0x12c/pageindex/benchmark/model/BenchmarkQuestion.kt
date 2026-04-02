package com.c0x12c.pageindex.benchmark.model

data class BenchmarkQuestion(
    val id: String,
    val question: String,
    val expectedAnswer: String,
    val documentId: String,
    val evidencePages: List<Int> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)
