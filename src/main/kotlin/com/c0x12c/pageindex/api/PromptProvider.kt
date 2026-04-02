package com.c0x12c.pageindex.api

import com.c0x12c.pageindex.api.model.PromptName

/**
 * Provides prompt templates by name.
 *
 * Default implementation [com.c0x12c.pageindex.core.prompt.ResourcePromptProvider] loads
 * prompts from classpath resources.
 */
interface PromptProvider {
  fun get(name: PromptName): String
}
