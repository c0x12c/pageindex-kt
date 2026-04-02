package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.StructureDetector
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.StructureDetectionResult

class NullDetector : StructureDetector {
  override suspend fun detect(pages: List<ParsedPage>, config: IndexingConfig): StructureDetectionResult? = null
}
