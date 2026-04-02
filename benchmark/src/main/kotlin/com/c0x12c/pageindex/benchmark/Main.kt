package com.c0x12c.pageindex.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.c0x12c.pageindex.benchmark.dataset.FinanceBenchDataset
import com.c0x12c.pageindex.benchmark.scorer.LlmJudgeScorer
import com.c0x12c.pageindex.core.llm.LiteLlmClient
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>): Unit = runBlocking {
    val datasetPath = args.getOrElse(0) { "benchmark/data/financebench_subset.jsonl" }
    val pdfDirectory = args.getOrElse(1) { "benchmark/data/pdfs" }

    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: error("Set OPENAI_API_KEY environment variable")
    val model = System.getenv("OPENAI_MODEL") ?: "gpt-4o"
    val judgeModel = System.getenv("JUDGE_MODEL") ?: model

    val config = BenchmarkConfig(
        datasetPath = datasetPath,
        pdfDirectory = pdfDirectory,
        openaiApiKey = apiKey,
        openaiModel = model,
        judgeModel = judgeModel,
    )

    val dataset = FinanceBenchDataset(config.datasetPath)
    val judgeClient = LiteLlmClient.openai(apiKey, judgeModel)
    val scorer = LlmJudgeScorer(judgeClient)
    val runner = BenchmarkRunner(config)

    println("=== PageIndex Benchmark ===")
    println("Dataset: ${config.datasetPath}")
    println("PDFs: ${config.pdfDirectory}")
    println("Model: ${config.openaiModel}")
    println("Judge: ${config.judgeModel}")
    println()

    val summary = runner.run(dataset, scorer)

    // Print results
    println()
    println("=== Results ===")
    println("Total: ${summary.totalQuestions}")
    println("Correct: ${summary.correct}")
    println("Partially correct: ${summary.partiallyCorrect}")
    println("Incorrect: ${summary.incorrect}")
    println("Errors: ${summary.errors}")
    println()
    println("Accuracy: ${"%.1f".format(summary.accuracy * 100)}%")
    println("Lenient accuracy: ${"%.1f".format(summary.lenientAccuracy * 100)}%")
    println("Avg query time: ${summary.avgQueryTimeMs}ms")
    println("Avg index time: ${summary.avgIndexTimeMs}ms")

    // Per-question detail
    println()
    println("=== Per-Question Detail ===")
    for (r in summary.results) {
        println("[${r.score}] ${r.questionId}: ${r.question}")
        println("  Expected: ${r.expectedAnswer.take(100)}")
        println("  Actual:   ${r.actualAnswer.take(100)}")
        println("  Pages: retrieved=${r.sourcePages}, expected=${r.expectedPages}")
        println()
    }

    // Write JSON output
    val outputDir = File(config.outputPath)
    outputDir.mkdirs()
    val mapper = jacksonObjectMapper()
    val outputFile = File(outputDir, "results.json")
    outputFile.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary))
    println("Results written to: ${outputFile.absolutePath}")
}
