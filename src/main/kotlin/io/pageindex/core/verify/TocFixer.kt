package io.pageindex.core.verify

import io.pageindex.api.LlmClient
import io.pageindex.api.PromptProvider
import io.pageindex.core.model.IncorrectItem
import io.pageindex.api.model.LlmMessage
import io.pageindex.api.model.LlmRole
import io.pageindex.api.model.ParsedPage
import io.pageindex.api.model.PromptName
import io.pageindex.core.model.PromptVar
import io.pageindex.api.model.TocEntry
import io.pageindex.core.prompt.PromptTemplate
import io.pageindex.core.util.LlmJsonParser
import io.pageindex.core.util.response.FixResponse

class TocFixer(
  private val llmClient: LlmClient,
  private val promptProvider: PromptProvider
) {

  private val physicalIndexRegex = Regex("<physical_index_(\\d+)>")

  suspend fun fix(
    entries: List<TocEntry>,
    incorrectItems: List<IncorrectItem>,
    taggedPages: List<ParsedPage>,
    maxAttempts: Int,
    searchRadius: Int
  ): List<TocEntry> {
    val result = entries.toMutableList()
    var attempts = 0

    for (incorrect in incorrectItems) {
      if (attempts >= maxAttempts) break
      attempts++

      val currentIndex = incorrect.entry.physicalIndex ?: continue
      val nearbyPages = taggedPages.filter {
        it.pageNumber in (currentIndex - searchRadius)..(currentIndex + searchRadius)
      }
      if (nearbyPages.isEmpty()) continue

      val pagesText = nearbyPages.joinToString("\n") { it.text }
      val prompt = PromptTemplate.render(
        promptProvider, PromptName.FIX,
        PromptVar.TITLE to incorrect.entry.title, PromptVar.PAGES_TEXT to pagesText
      )
      val response = llmClient.chat(
        listOf(LlmMessage(LlmRole.USER, prompt)),
        tags = listOf("fix")
      )
      val parsed = LlmJsonParser.parseAs(response, FixResponse::class.java)
      val physicalIndexStr = parsed?.physicalIndex ?: continue
      val match = physicalIndexRegex.find(physicalIndexStr) ?: continue
      val newIndex = match.groupValues[1].toIntOrNull() ?: continue

      val entryIndex = result.indexOfFirst { it === incorrect.entry }
      if (entryIndex >= 0) {
        result[entryIndex] = result[entryIndex].copy(physicalIndex = newIndex)
      }
    }

    return result
  }
}
