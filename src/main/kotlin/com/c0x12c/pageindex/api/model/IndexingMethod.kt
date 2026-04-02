package com.c0x12c.pageindex.api.model

enum class IndexingMethod {
  TOC_WITH_PAGES,
  TOC_WITHOUT_PAGES,
  HEADER_BASED,
  LLM_DETECTED,
  REGEX_DISCOVERED,
  FLAT_PAGES
}
