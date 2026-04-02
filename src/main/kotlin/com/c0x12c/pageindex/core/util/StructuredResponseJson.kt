package com.c0x12c.pageindex.core.util

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.c0x12c.pageindex.core.util.JacksonUtils.createObjectMapper

object StructuredResponseJson {

  const val STRUCTURED_LIST_ITEMS_PROPERTY: String = "items"

  private val mapper = JacksonUtils.createObjectMapper()

  fun writeValueAsString(value: Any): String = mapper.writeValueAsString(value)

  fun <T> readObject(json: String, clazz: Class<T>): T? =
    try {
      val normalized = JacksonUtils.normalizeJsonKeys(json.trim())
      mapper.readValue(normalized, clazz)
    } catch (_: JsonProcessingException) {
      null
    }

  fun <T> readList(json: String, elementType: Class<T>): List<T>? =
    try {
      val normalized = JacksonUtils.normalizeJsonKeys(json.trim())
      val type = mapper.typeFactory.constructCollectionType(List::class.java, elementType)
      mapper.readValue(normalized, type)
    } catch (_: JsonProcessingException) {
      null
    }

  fun <T> readStructuredList(json: String, elementType: Class<T>): List<T>? {
    val trimmed = json.trim()
    return when {
      trimmed.startsWith("[") -> readList(trimmed, elementType)
      trimmed.startsWith("{") ->
        readListFromItemsProperty(trimmed, elementType, STRUCTURED_LIST_ITEMS_PROPERTY)

      else -> null
    }
  }

  private fun <T> readListFromItemsProperty(
    json: String,
    elementType: Class<T>,
    propertyName: String
  ): List<T>? {
    return try {
      val root: JsonNode = mapper.readTree(json)
      if (!root.isObject) return null
      val itemsNode = root.get(propertyName) ?: return null
      if (!itemsNode.isArray) return null
      val type = mapper.typeFactory.constructCollectionType(List::class.java, elementType)
      mapper.convertValue(itemsNode, type)
    } catch (_: JsonProcessingException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }
}
