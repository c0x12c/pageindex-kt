package io.pageindex.core.model

data class VerificationResult(
  val accuracy: Double,
  val incorrectItems: List<IncorrectItem>
)
