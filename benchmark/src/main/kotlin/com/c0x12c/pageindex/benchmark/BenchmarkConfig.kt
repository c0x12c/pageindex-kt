package com.c0x12c.pageindex.benchmark

import com.c0x12c.pageindex.api.model.IndexingConfig

data class BenchmarkConfig(
    val datasetPath: String,
    val pdfDirectory: String,
    val openaiApiKey: String,
    val openaiModel: String = "gpt-4o",
    val judgeModel: String = "gpt-4o",
    val indexingConfig: IndexingConfig = IndexingConfig(),
    val outputPath: String = "benchmark/output",
) {

    fun toMap(): Map<String, String> = mapOf(
        "dataset_path" to datasetPath,
        "pdf_directory" to pdfDirectory,
        "openai_model" to openaiModel,
        "judge_model" to judgeModel,
        "output_path" to outputPath,
    )
}
