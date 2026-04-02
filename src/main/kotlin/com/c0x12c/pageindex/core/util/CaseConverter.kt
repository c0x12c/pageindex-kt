package com.c0x12c.pageindex.core.util

object CaseConverter {
  fun camelToSnake(input: String): String {
    if (input.isEmpty()) return input

    if (input.contains('_')) {
      return input.split('_').joinToString("_") { convertCamelPart(it) }
    }

    return convertCamelPart(input)
  }

  private fun convertCamelPart(part: String): String {
    if (part.isEmpty()) return part
    if (part == part.lowercase()) return part

    return buildString {
      for ((index, char) in part.withIndex()) {
        when {
          char.isUpperCase() -> {
            if (index > 0) {
              val prevChar = part[index - 1]
              val nextIsLower = index + 1 < part.length && part[index + 1].isLowerCase()
              if (!prevChar.isUpperCase() || nextIsLower) {
                append('_')
              }
            }
            append(char.lowercaseChar())
          }
          else -> append(char)
        }
      }
    }
  }
}
