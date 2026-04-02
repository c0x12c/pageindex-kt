package com.c0x12c.pageindex.core.util

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.c0x12c.pageindex.core.util.CaseConverter.camelToSnake

object SchemaHintGenerator {

  fun generate(clazz: Class<*>): String {
    val fields = clazz.declaredFields
      .filter { !it.isSynthetic }
      .associate { field ->
        val key = camelToSnake(field.name)
        val type = typeHint(field.type)
        val description = field.getAnnotation(JsonPropertyDescription::class.java)?.value
        val hint = if (description != null) "$type - $description" else type
        key to hint
      }

    return StructuredResponseJson.writeValueAsString(fields)
  }

  private fun typeHint(type: Class<*>): String = when {
    type == String::class.java || type == java.lang.String::class.java -> "string"
    type == Int::class.java || type == java.lang.Integer::class.java -> "number"
    type == Long::class.java || type == java.lang.Long::class.java -> "number"
    type == Double::class.java || type == java.lang.Double::class.java -> "number"
    type == Float::class.java || type == java.lang.Float::class.java -> "number"
    type == Boolean::class.java || type == java.lang.Boolean::class.java -> "boolean"
    List::class.java.isAssignableFrom(type) -> "array"
    Map::class.java.isAssignableFrom(type) -> "object"
    else -> "string"
  }
}
