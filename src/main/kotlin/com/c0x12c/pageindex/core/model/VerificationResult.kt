package com.c0x12c.pageindex.core.model

data class VerificationResult(
  val accuracy: Double,
  val incorrectItems: List<IncorrectItem>
)
