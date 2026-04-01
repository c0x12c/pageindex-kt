package io.pageindex.api

import java.io.Serializable

open class PageIndexException(
  val httpStatusCode: Int,
  val code: String,
  override val message: String = ""
) : RuntimeException(message),
  Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
