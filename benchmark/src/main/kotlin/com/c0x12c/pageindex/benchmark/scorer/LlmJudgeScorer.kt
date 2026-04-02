package com.c0x12c.pageindex.benchmark.scorer

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.benchmark.model.Score

class LlmJudgeScorer(private val llmClient: LlmClient) : BenchmarkScorer {

    private val mapper = jacksonObjectMapper()

    override suspend fun score(
        question: String,
        expectedAnswer: String,
        actualAnswer: String,
    ): ScoringResult {
        val prompt = buildPrompt(question, expectedAnswer, actualAnswer)
        val messages = listOf(
            LlmMessage(LlmRole.SYSTEM, SYSTEM_PROMPT),
            LlmMessage(LlmRole.USER, prompt),
        )

        return try {
            val response = llmClient.chat(messages)
            parseResponse(response)
        } catch (e: Exception) {
            ScoringResult(Score.ERROR, "Scoring failed: ${e.message}")
        }
    }

    private fun parseResponse(response: String): ScoringResult {
        val json = extractJson(response)
        val parsed = mapper.readValue(json, JudgeResponse::class.java)
        val score = when (parsed.score.uppercase()) {
            "CORRECT" -> Score.CORRECT
            "PARTIALLY_CORRECT" -> Score.PARTIALLY_CORRECT
            "INCORRECT" -> Score.INCORRECT
            else -> Score.ERROR
        }
        return ScoringResult(score, parsed.reasoning)
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1) return text
        return text.substring(start, end + 1)
    }

    private fun buildPrompt(question: String, expectedAnswer: String, actualAnswer: String): String = """
        Evaluate if the actual answer is correct for the given question.

        Question: $question

        Expected Answer: $expectedAnswer

        Actual Answer: $actualAnswer

        Rules:
        - CORRECT: The actual answer matches the expected answer in meaning and key facts.
        - PARTIALLY_CORRECT: The actual answer has some correct info but is incomplete or has minor errors.
        - INCORRECT: The actual answer is wrong, irrelevant, or contradicts the expected answer.
        - For numerical answers, small rounding differences are acceptable.
        - Focus on factual correctness, not writing style.

        Respond in JSON only:
        {"score": "CORRECT|PARTIALLY_CORRECT|INCORRECT", "reasoning": "brief explanation"}
    """.trimIndent()

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class JudgeResponse(val score: String, val reasoning: String)

    companion object {
        private val SYSTEM_PROMPT = """
            You are a benchmark evaluator. You compare an actual answer against an expected answer.
            You must respond with valid JSON only. No other text.
        """.trimIndent()
    }
}
