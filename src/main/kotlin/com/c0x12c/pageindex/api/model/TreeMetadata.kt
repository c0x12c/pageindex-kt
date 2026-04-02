package com.c0x12c.pageindex.api.model

data class TreeMetadata(
  val totalNodes: Int,
  val totalPages: Int,
  val maxDepth: Int,
  val indexingModel: String,
  val documentTitle: String? = null,
  val indexingMethod: IndexingMethod
)
