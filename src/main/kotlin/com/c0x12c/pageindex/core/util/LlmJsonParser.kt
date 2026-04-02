package com.c0x12c.pageindex.core.util

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.c0x12c.pageindex.core.util.CaseConverter.camelToSnake
import com.c0x12c.pageindex.core.util.JacksonUtils.createObjectMapper

object LlmJsonParser {
  private val mapper = JacksonUtils.createObjectMapper()
  private val CODE_BLOCK_REGEX = Regex("```(?:json)?\\s*\\n([\\s\\S]*?)\\n\\s*```")
  private val CODE_BLOCK_START = Regex("^```(?:json)?\\s*\n?")
  private val CODE_BLOCK_END = Regex("\\s*```\\s*$")

  private val TRAILING_COMMA_ARRAY = Regex(",\\s*]")
  private val TRAILING_COMMA_OBJECT = Regex(",\\s*}")
  private val PYTHON_NONE = Regex("\\bNone\\b")

  private fun clean(response: String): String {
    var text = response

    val codeBlock = CODE_BLOCK_REGEX.find(text)
    text = if (codeBlock != null) {
      codeBlock.groupValues[1].trim()
    } else {
      text
        .replace(CODE_BLOCK_START, "")
        .replace(CODE_BLOCK_END, "")
        .trim()
    }

    text = text
      .replace(TRAILING_COMMA_ARRAY, "]")
      .replace(TRAILING_COMMA_OBJECT, "}")
      .replace(PYTHON_NONE, "null")

    if (!text.startsWith("{") && !text.startsWith("[")) {
      val start = text.indexOfFirst { it == '{' || it == '[' }
      if (start >= 0) {
        val close = if (text[start] == '{') '}' else ']'
        val end = text.lastIndexOf(close)
        if (end > start) text = text.substring(start, end + 1)
      }
    }

    return text
  }

  fun <T> parse(response: String, typeRef: TypeReference<T>): T? {
    return try {
      mapper.readValue(clean(response), typeRef)
    } catch (_: JsonProcessingException) {
      null
    }
  }

  fun <T> parseAs(response: String, clazz: Class<T>): T? {
    return try {
      val normalized = JacksonUtils.normalizeJsonKeys(clean(response))
      mapper.readValue(normalized, clazz)
    } catch (_: JsonProcessingException) {
      null
    }
  }

  fun <T> parseListAs(response: String, clazz: Class<T>): List<T>? {
    return try {
      val normalized = JacksonUtils.normalizeJsonKeys(clean(response))
      val type = mapper.typeFactory.constructCollectionType(List::class.java, clazz)
      mapper.readValue(normalized, type)
    } catch (_: JsonProcessingException) {
      null
    }
  }

  fun parseMap(response: String): Map<String, Any?>? =
    parse(response, object : TypeReference<Map<String, Any?>>() {})
      ?.let { normalizeKeys(it) }

  fun parseList(response: String): List<Map<String, Any?>>? =
    parse(response, object : TypeReference<List<Map<String, Any?>>>() {})
      ?.map { normalizeKeys(it) }

  fun writeValueAsString(value: Any): String = mapper.writeValueAsString(value)

  private fun normalizeKeys(map: Map<String, Any?>): Map<String, Any?> =
    map.entries.associate { (key, value) ->
      camelToSnake(key) to normalizeValue(value)
    }

  private fun normalizeValue(value: Any?): Any? = when (value) {
    is Map<*, *> -> normalizeKeys(
      value.entries.associate { (k, v) -> k.toString() to v }
    )
    is List<*> -> value.map { normalizeValue(it) }
    else -> value
  }
}
