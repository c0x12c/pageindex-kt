package com.c0x12c.pageindex.core.model

import com.c0x12c.pageindex.api.model.TocEntry

data class IncorrectItem(
  val entry: TocEntry,
  val expectedPhysicalIndex: Int?,
  val reason: String
)
