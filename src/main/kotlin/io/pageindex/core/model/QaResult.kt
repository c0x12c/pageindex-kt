package io.pageindex.core.model

import com.fasterxml.jackson.annotation.JsonPropertyDescription

data class QaResult(
  @field:JsonPropertyDescription("Direct answer to the user's question based on the document sections")
  val answer: String,
  @field:JsonPropertyDescription("Explanation of which parts of the document support the answer and why they are relevant to the question")
  val reasoning: String
)
