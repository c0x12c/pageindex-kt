package com.c0x12c.pageindex.api.model

data class StructureDetectionResult(
  val entries: List<TocEntry>,
  val method: IndexingMethod,
  val rawTocText: String? = null,
  // The last page number that contains actual document content (excludes structural pages
  // such as a TOC at the end of the document). When null, defaults to the total page count.
  val lastContentPage: Int? = null
)
