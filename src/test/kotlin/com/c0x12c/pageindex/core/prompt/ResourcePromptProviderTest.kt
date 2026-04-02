package com.c0x12c.pageindex.core.prompt

import com.c0x12c.pageindex.api.model.PromptName
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResourcePromptProviderTest {

  private val provider = ResourcePromptProvider()

  @Test
  fun `get - loads all prompt files without error`() {
    for (name in PromptName.entries) {
      val template = provider.get(name)
      assertNotNull(template, "Failed to load prompt: ${name.fileName}")
      assertTrue(template.isNotBlank(), "Prompt is blank: ${name.fileName}")
    }
  }

  @Test
  fun `get - caches loaded templates`() {
    val first = provider.get(PromptName.SUMMARY)
    val second = provider.get(PromptName.SUMMARY)

    assertSame(first, second)
  }

  @Test
  fun `get - summary prompt contains sections placeholder`() {
    val summary = provider.get(PromptName.SUMMARY)
    assertTrue(summary.contains("{{sections}}"))
  }
}
