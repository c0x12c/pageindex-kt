package com.c0x12c.pageindex.core.prompt

import com.c0x12c.pageindex.api.PromptProvider
import com.c0x12c.pageindex.api.model.PromptName

object PromptTemplate {
  fun render(provider: PromptProvider, name: PromptName, vars: Map<String, String>): String {
    var template = provider.get(name)
    for ((key, value) in vars) {
      template = template.replace("{{$key}}", value)
    }
    return template
  }

  fun render(provider: PromptProvider, name: PromptName, vararg pairs: Pair<String, String>): String =
    render(provider, name, pairs.toMap())
}
