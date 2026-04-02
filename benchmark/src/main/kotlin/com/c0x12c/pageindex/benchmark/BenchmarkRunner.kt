package com.c0x12c.pageindex.benchmark

import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.benchmark.dataset.BenchmarkDataset
import com.c0x12c.pageindex.benchmark.model.BenchmarkQuestion
import com.c0x12c.pageindex.benchmark.model.BenchmarkSummary
import com.c0x12c.pageindex.benchmark.model.QuestionResult
import com.c0x12c.pageindex.benchmark.model.Score
import com.c0x12c.pageindex.benchmark.pdf.PdfParser
import com.c0x12c.pageindex.benchmark.scorer.BenchmarkScorer
import com.c0x12c.pageindex.core.PageIndex
import com.c0x12c.pageindex.core.llm.LiteLlmClient
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

class BenchmarkRunner(private val config: BenchmarkConfig) {

    private val logger = LoggerFactory.getLogger(BenchmarkRunner::class.java)

    suspend fun run(dataset: BenchmarkDataset, scorer: BenchmarkScorer): BenchmarkSummary {
        val questions = dataset.load()
        logger.info("Loaded {} questions from {}", questions.size, dataset.name)

        val llmClient = LiteLlmClient.openai(config.openaiApiKey, config.openaiModel)
        val pageIndex = PageIndex.create { this.llmClient = llmClient }

        // Index each unique document
        val documentIndex = mutableMapOf<String, IndexedDocument>()
        val documentIds = dataset.documentIds()

        for (docName in documentIds) {
            val pdfFile = File(config.pdfDirectory, "$docName.pdf")
            if (!pdfFile.exists()) {
                logger.warn("PDF not found, skipping: {}", pdfFile.absolutePath)
                continue
            }

            logger.info("Indexing document: {}", docName)
            val pages = PdfParser.parse(pdfFile)
            val docId = UUID.randomUUID()
            val projectId = UUID.randomUUID()

            val startTime = System.currentTimeMillis()
            val result = pageIndex.buildAndSave(docId, projectId, pages, config.indexingConfig)
            val indexTimeMs = System.currentTimeMillis() - startTime

            result.fold(
                { error ->
                    logger.error("Failed to index {}: {}", docName, error.message)
                },
                { tree ->
                    logger.info("Indexed {} in {}ms ({} nodes)", docName, indexTimeMs, tree.metadata.totalNodes)
                    documentIndex[docName] = IndexedDocument(docId, indexTimeMs)
                },
            )
        }

        // Run questions
        val results = questions.map { q -> runQuestion(q, documentIndex, pageIndex, scorer) }

        return buildSummary(results)
    }

    private suspend fun runQuestion(
        question: BenchmarkQuestion,
        documentIndex: Map<String, IndexedDocument>,
        pageIndex: com.c0x12c.pageindex.api.PageIndexManager,
        scorer: BenchmarkScorer,
    ): QuestionResult {
        val indexed = documentIndex[question.documentId]
        if (indexed == null) {
            logger.warn("No index for document: {}, skipping question: {}", question.documentId, question.id)
            return errorResult(question, 0, "Document not indexed: ${question.documentId}")
        }

        logger.info("Running question: {}", question.id)
        val messages = listOf(LlmMessage(LlmRole.USER, question.question))

        val startTime = System.currentTimeMillis()
        val queryResult = pageIndex.query(indexed.uuid, messages)
        val queryTimeMs = System.currentTimeMillis() - startTime

        return queryResult.fold(
            { error ->
                logger.error("Query failed for {}: {}", question.id, error.message)
                errorResult(question, indexed.indexTimeMs, "Query failed: ${error.message}")
            },
            { answer ->
                val scoring = scorer.score(question.question, question.expectedAnswer, answer.data)
                logger.info("Question {} scored: {}", question.id, scoring.score)

                QuestionResult(
                    questionId = question.id,
                    question = question.question,
                    expectedAnswer = question.expectedAnswer,
                    actualAnswer = answer.data,
                    score = scoring.score,
                    reasoning = scoring.reasoning,
                    sourcePages = answer.sourcePages,
                    expectedPages = question.evidencePages,
                    indexingTimeMs = indexed.indexTimeMs,
                    queryTimeMs = queryTimeMs,
                )
            },
        )
    }

    private fun errorResult(question: BenchmarkQuestion, indexTimeMs: Long, message: String) = QuestionResult(
        questionId = question.id,
        question = question.question,
        expectedAnswer = question.expectedAnswer,
        actualAnswer = "ERROR: $message",
        score = Score.ERROR,
        reasoning = message,
        sourcePages = emptyList(),
        expectedPages = question.evidencePages,
        indexingTimeMs = indexTimeMs,
        queryTimeMs = 0,
    )

    private fun buildSummary(results: List<QuestionResult>): BenchmarkSummary {
        val correct = results.count { it.score == Score.CORRECT }
        val partial = results.count { it.score == Score.PARTIALLY_CORRECT }
        val incorrect = results.count { it.score == Score.INCORRECT }
        val errors = results.count { it.score == Score.ERROR }
        val total = results.size

        return BenchmarkSummary(
            totalQuestions = total,
            correct = correct,
            partiallyCorrect = partial,
            incorrect = incorrect,
            errors = errors,
            accuracy = if (total > 0) correct.toDouble() / total else 0.0,
            lenientAccuracy = if (total > 0) (correct + partial).toDouble() / total else 0.0,
            avgQueryTimeMs = if (results.isNotEmpty()) results.map { it.queryTimeMs }.average().toLong() else 0,
            avgIndexTimeMs = if (results.isNotEmpty()) {
                results.map { it.indexingTimeMs }.distinct().average().toLong()
            } else 0,
            results = results,
            config = config.toMap(),
        )
    }

    private data class IndexedDocument(val uuid: UUID, val indexTimeMs: Long)
}
