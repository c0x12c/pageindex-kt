package io.pageindex.core.model

import io.pageindex.api.model.TocEntry

data class IncorrectItem(
  val entry: TocEntry,
  val expectedPhysicalIndex: Int?,
  val reason: String
)
