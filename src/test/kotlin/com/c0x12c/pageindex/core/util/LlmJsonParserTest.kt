package com.c0x12c.pageindex.core.util

import com.fasterxml.jackson.core.type.TypeReference
import com.c0x12c.pageindex.core.util.response.FixResponse
import com.c0x12c.pageindex.core.util.response.VerificationResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LlmJsonParserTest {

  @Test
  fun `parse - parses clean JSON object`() {
    val json = """{"key": "value", "number": 42}"""

    val result = LlmJsonParser.parseMap(json)

    assertNotNull(result)
    assertEquals("value", result?.get("key"))
    assertEquals(42, result?.get("number"))
  }

  @Test
  fun `parse - parses clean JSON array`() {
    val json = """[{"a": 1}, {"a": 2}]"""

    val result = LlmJsonParser.parseList(json)

    assertNotNull(result)
    assertEquals(2, result?.size)
  }

  @Test
  fun `parse - strips json code block wrapper`() {
    val json = "```json\n{\"toc_detected\": \"yes\"}\n```"

    val result = LlmJsonParser.parseMap(json)

    assertNotNull(result)
    assertEquals("yes", result?.get("toc_detected"))
  }

  @Test
  fun `parse - strips code block wrapper without json tag`() {
    val json = "```\n{\"completed\": \"no\"}\n```"

    val result = LlmJsonParser.parseMap(json)

    assertNotNull(result)
    assertEquals("no", result?.get("completed"))
  }

  @Test
  fun `parse - returns null on invalid JSON`() {
    val result = LlmJsonParser.parseMap("this is not json at all")

    assertNull(result)
  }

  @Test
  fun `parse - handles extra whitespace around code blocks`() {
    val json = "```json\n{\"key\": \"val\"}\n```  "

    val result = LlmJsonParser.parseMap(json)

    assertNotNull(result)
    assertEquals("val", result?.get("key"))
  }

  @Test
  fun `parseMap - returns map from JSON object`() {
    val json = """{"status": "ok", "count": 3}"""

    val result = LlmJsonParser.parseMap(json)

    assertNotNull(result)
    assertEquals("ok", result?.get("status"))
    assertEquals(3, result?.get("count"))
  }

  @Test
  fun `parseMap - returns null from JSON array`() {
    val json = """[1, 2, 3]"""

    val result = LlmJsonParser.parseMap(json)

    assertNull(result)
  }

  @Test
  fun `parseList - returns list from JSON array`() {
    val json = """[{"title": "Intro", "page": 1}, {"title": "Methods", "page": 5}]"""

    val result = LlmJsonParser.parseList(json)

    assertNotNull(result)
    assertEquals(2, result?.size)
    assertEquals("Intro", result?.get(0)?.get("title"))
    assertEquals(1, result?.get(0)?.get("page"))
  }

  @Test
  fun `parseList - returns null from JSON object`() {
    val json = """{"table_of_contents": []}"""

    val result = LlmJsonParser.parseList(json)

    assertNull(result)
  }

  @Test
  fun `parse - generic parse returns typed result`() {
    val json = """{"name": "test", "value": 99}"""

    val result = LlmJsonParser.parse(json, object : TypeReference<Map<String, Any?>>() {})

    assertNotNull(result)
    assertEquals("test", result?.get("name"))
  }

  @Test
  fun `parseAs - deserializes typed response with snake_case keys`() {
    val json = """{"thinking": "looks good", "answer": "yes"}"""

    val result = LlmJsonParser.parseAs(json, VerificationResponse::class.java)

    assertNotNull(result)
    assertEquals("looks good", result?.thinking)
    assertEquals("yes", result?.answer)
  }

  @Test
  fun `parseAs - deserializes typed response with camelCase keys`() {
    val json = """{"physicalIndex": "<physical_index_5>", "thinking": "found it"}"""

    val result = LlmJsonParser.parseAs(json, FixResponse::class.java)

    assertNotNull(result)
    assertEquals("<physical_index_5>", result?.physicalIndex)
    assertEquals("found it", result?.thinking)
  }

  @Test
  fun `parseAs - returns null for invalid JSON`() {
    val result = LlmJsonParser.parseAs("not json", VerificationResponse::class.java)

    assertNull(result)
  }

  @Test
  fun `parseAs - ignores unknown fields from LLM`() {
    val json = """{"answer": "yes", "confidence": 0.95, "extra_field": true}"""

    val result = LlmJsonParser.parseAs(json, VerificationResponse::class.java)

    assertNotNull(result)
    assertEquals("yes", result?.answer)
  }

  @Test
  fun `parseListAs - deserializes typed list with mixed case keys`() {
    val json = """[{"physicalIndex": "3", "thinking": "a"}, {"physical_index": "5", "thinking": "b"}]"""

    val result = LlmJsonParser.parseListAs(json, FixResponse::class.java)

    assertNotNull(result)
    assertEquals(2, result?.size)
    assertEquals("3", result?.get(0)?.physicalIndex)
    assertEquals("5", result?.get(1)?.physicalIndex)
  }

  @Test
  fun `parseMap - normalizes camelCase keys to snake_case`() {
    val json = """{"physicalIndex": "3", "tocDetected": "yes"}"""

    val result = LlmJsonParser.parseMap(json)

    assertNotNull(result)
    assertEquals("3", result?.get("physical_index"))
    assertEquals("yes", result?.get("toc_detected"))
  }

  @Test
  fun `parseMap - keeps snake_case keys unchanged`() {
    val json = """{"physical_index": "3", "toc_detected": "yes"}"""

    val result = LlmJsonParser.parseMap(json)

    assertNotNull(result)
    assertEquals("3", result?.get("physical_index"))
    assertEquals("yes", result?.get("toc_detected"))
  }

  @Test
  fun `parseList - normalizes camelCase keys in list items`() {
    val json = """[{"physicalIndex": "1", "title": "Intro"}, {"physicalIndex": "2", "title": "Methods"}]"""

    val result = LlmJsonParser.parseList(json)

    assertNotNull(result)
    assertEquals(2, result?.size)
    assertEquals("1", result?.get(0)?.get("physical_index"))
    assertEquals("Intro", result?.get(0)?.get("title"))
  }

  @Test
  fun `parse - handles trailing comma in array`() {
    val json = """[{"key": "a"}, {"key": "b"},]"""

    val result = LlmJsonParser.parseList(json)

    assertNotNull(result)
    assertEquals(2, result?.size)
  }

  @Test
  fun `parse - handles trailing comma in object`() {
    val json = """{"key": "value", "num": 1,}"""

    val result = LlmJsonParser.parseMap(json)

    assertNotNull(result)
    assertEquals("value", result?.get("key"))
  }

  @Test
  fun `parse - replaces Python None with null`() {
    val json = """{"key": "value", "optional": None}"""

    val result = LlmJsonParser.parseMap(json)

    assertNotNull(result)
    assertEquals("value", result?.get("key"))
    assertNull(result?.get("optional"))
  }
}
