package com.c0x12c.pageindex.core.prompt

import com.c0x12c.pageindex.api.PromptProvider
import com.c0x12c.pageindex.api.model.PromptName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PromptTemplateTest {

  private val fakeProvider = object : PromptProvider {
    override fun get(name: PromptName): String = when (name) {
      PromptName.SUMMARY -> "Summarize: {{sections}}"
      PromptName.NODE_SELECTION -> "Select: {{query}} from {{tree}}"
      else -> "template with {{key}}"
    }
  }

  @Test
  fun `render - replaces single variable`() {
    val result = PromptTemplate.render(
      fakeProvider, PromptName.SUMMARY,
      mapOf("sections" to "hello world")
    )

    assertEquals("Summarize: hello world", result)
  }

  @Test
  fun `render - replaces multiple variables`() {
    val result = PromptTemplate.render(
      fakeProvider, PromptName.NODE_SELECTION,
      mapOf("query" to "find this", "tree" to "my tree")
    )

    assertEquals("Select: find this from my tree", result)
  }

  @Test
  fun `render - leaves template unchanged when no variables match`() {
    val result = PromptTemplate.render(
      fakeProvider, PromptName.SUMMARY,
      mapOf("wrong_key" to "value")
    )

    assertEquals("Summarize: {{sections}}", result)
  }

  @Test
  fun `render - handles empty vars map`() {
    val result = PromptTemplate.render(
      fakeProvider, PromptName.SUMMARY,
      emptyMap()
    )

    assertEquals("Summarize: {{sections}}", result)
  }

  @Test
  fun `render - vararg overload works`() {
    val result = PromptTemplate.render(
      fakeProvider, PromptName.SUMMARY,
      "sections" to "vararg test"
    )

    assertEquals("Summarize: vararg test", result)
  }
}
