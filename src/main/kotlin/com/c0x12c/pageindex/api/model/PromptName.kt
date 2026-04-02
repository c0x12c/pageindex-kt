package com.c0x12c.pageindex.api.model

enum class PromptName(val fileName: String) {
  TOC_DETECTION("toc-detection"),
  TOC_EXTRACTION("toc-extraction"),
  TOC_COMPLETENESS_CHECK("toc-completeness-check"),
  TOC_TRANSFORM("toc-transform"),
  PAGE_INDEX_DETECTION("page-index-detection"),
  TOC_PAGE_MATCHING("toc-page-matching"),
  STRUCTURE_DETECTION_INIT("structure-detection-init"),
  STRUCTURE_DETECTION_CONTINUE("structure-detection-continue"),
  STRUCTURE_DETECTION_MERGE("structure-detection-merge"),
  VERIFICATION("verification"),
  FIX("fix"),
  SUMMARY("summary"),
  ROLLUP_SUMMARY("rollup-summary"),
  NODE_SELECTION("node-selection"),
  ANSWER_GENERATION("answer-generation"),
  REGEX_DISCOVERY("regex-discovery")
}
