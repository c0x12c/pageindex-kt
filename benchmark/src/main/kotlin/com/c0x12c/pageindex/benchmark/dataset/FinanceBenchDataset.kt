package com.c0x12c.pageindex.benchmark.dataset

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.c0x12c.pageindex.benchmark.model.BenchmarkQuestion
import java.io.File

class FinanceBenchDataset(private val jsonlPath: String) : BenchmarkDataset {

    override val name: String = "FinanceBench"

    private val mapper = jacksonObjectMapper()

    override fun load(): List<BenchmarkQuestion> {
        val file = File(jsonlPath)
        require(file.exists()) { "Dataset file not found: $jsonlPath" }

        return file.readLines()
            .filter { it.isNotBlank() }
            .map { line -> mapper.readValue(line, FinanceBenchEntry::class.java) }
            .map { entry -> entry.toBenchmarkQuestion() }
    }

    override fun documentIds(): Set<String> = load().map { it.documentId }.toSet()

    private fun FinanceBenchEntry.toBenchmarkQuestion() = BenchmarkQuestion(
        id = financebenchId,
        question = question,
        expectedAnswer = answer,
        documentId = docName,
        evidencePages = evidence.mapNotNull { it.evidencePageNum },
        metadata = buildMap {
            company?.let { put("company", it) }
            questionType?.let { put("question_type", it) }
            questionReasoning?.let { put("question_reasoning", it) }
            justification?.let { put("justification", it) }
        },
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class FinanceBenchEntry(
    @JsonProperty("financebench_id") val financebenchId: String,
    val company: String? = null,
    @JsonProperty("doc_name") val docName: String,
    @JsonProperty("question_type") val questionType: String? = null,
    @JsonProperty("question_reasoning") val questionReasoning: String? = null,
    val question: String,
    val answer: String,
    val justification: String? = null,
    val evidence: List<FinanceBenchEvidence> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FinanceBenchEvidence(
    @JsonProperty("evidence_text") val evidenceText: String? = null,
    @JsonProperty("doc_name") val docName: String? = null,
    @JsonProperty("evidence_page_num") val evidencePageNum: Int? = null,
)
