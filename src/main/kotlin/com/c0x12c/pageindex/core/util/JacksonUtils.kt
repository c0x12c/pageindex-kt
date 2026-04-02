package com.c0x12c.pageindex.core.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object JacksonUtils {
  /**
   * Creates an ObjectMapper with snake_case naming and flexible deserialization.
   * Use [normalizeJsonKeys] before deserializing LLM responses that may have mixed casing.
   */
  fun createObjectMapper(): ObjectMapper =
    jacksonObjectMapper().apply {
      propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
      configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

  /**
   * Normalizes all JSON object keys to snake_case.
   * LLMs output JSON with inconsistent key casing (camelCase, snake_case, PascalCase).
   * This ensures all keys are snake_case before deserialization.
   */
  fun normalizeJsonKeys(json: String): String {
    val mapper = jacksonObjectMapper()
    return try {
      val tree = mapper.readTree(json)
      normalizeNode(tree)
      mapper.writeValueAsString(tree)
    } catch (_: Exception) {
      json
    }
  }

  private fun normalizeNode(node: JsonNode) {
    when {
      node.isObject -> {
        val obj = node as ObjectNode
        val entries = obj.fields().asSequence().toList()
        obj.removeAll()
        for ((key, value) in entries) {
          normalizeNode(value)
          obj.set<JsonNode>(CaseConverter.camelToSnake(key), value)
        }
      }
      node.isArray -> {
        val arr = node as ArrayNode
        for (element in arr) {
          normalizeNode(element)
        }
      }
    }
  }
}
