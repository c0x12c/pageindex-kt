package com.c0x12c.pageindex.core.prompt

import com.c0x12c.pageindex.api.PromptProvider
import com.c0x12c.pageindex.api.model.PromptName
import java.util.concurrent.ConcurrentHashMap

class ResourcePromptProvider : PromptProvider {
  private val cache = ConcurrentHashMap<PromptName, String>()

  override fun get(name: PromptName): String {
    return cache.getOrPut(name) {
      val path = "prompts/${name.fileName}.md"
      javaClass.classLoader?.getResourceAsStream(path)
        ?.bufferedReader()?.readText()
        ?: error("Prompt resource not found: $path")
    }
  }
}
