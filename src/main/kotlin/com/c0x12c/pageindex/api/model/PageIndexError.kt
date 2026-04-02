package com.c0x12c.pageindex.api.model

import com.c0x12c.pageindex.api.PageIndexException

enum class PageIndexError(
  val statusCode: Int,
  val code: String,
  val message: String
) {
  NO_PAGES_PROVIDED(400, "PAGE_INDEX_NO_PAGES", "No pages provided"),
  STRUCTURE_DETECTION_FAILED(500, "PAGE_INDEX_STRUCTURE_FAILED", "Structure detection failed"),
  INDEX_NOT_FOUND(404, "PAGE_INDEX_NOT_FOUND", "Document index not found"),
  NODE_SELECTION_PARSE_FAILED(500, "PAGE_INDEX_SELECTION_FAILED", "Failed to parse node selection response"),
  STRUCTURED_CHAT_PARSE_FAILED(500, "STRUCTURED_CHAT_PARSE_FAILED", "Failed to parse LLM response into requested structure");

  fun asException(): PageIndexException = asException(null)

  fun asException(customMessage: String?): PageIndexException =
    PageIndexException(
      httpStatusCode = statusCode,
      code = code,
      message = customMessage ?: message
    )
}
