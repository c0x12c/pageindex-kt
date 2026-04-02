package com.c0x12c.pageindex.core.model

import com.fasterxml.jackson.annotation.JsonPropertyDescription

data class NodeSelectionResult(
  @field:JsonPropertyDescription("List of node IDs from the document index that are most relevant")
  val selectedNodeIds: List<String>,
  @field:JsonPropertyDescription("Brief reasoning for why these nodes were selected")
  val reasoning: String
)
