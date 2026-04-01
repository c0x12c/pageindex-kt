package io.pageindex.core.verify

import io.pageindex.api.LlmClient
import io.pageindex.api.PromptProvider
import io.pageindex.core.model.IncorrectItem
import io.pageindex.core.model.LlmField
import io.pageindex.api.model.LlmMessage
import io.pageindex.api.model.LlmRole
import io.pageindex.api.model.ParsedPage
import io.pageindex.api.model.PromptName
import io.pageindex.core.model.PromptVar
import io.pageindex.api.model.TocEntry
import io.pageindex.core.model.VerificationResult
import io.pageindex.core.prompt.PromptTemplate
import io.pageindex.core.util.LlmJsonParser
import io.pageindex.core.util.response.VerificationResponse

class TocVerifier(
  private val llmClient: LlmClient,
  private val promptProvider: PromptProvider
) {

  suspend fun verify(
    entries: List<TocEntry>,
    taggedPages: List<ParsedPage>,
    sampleSize: Int
  ): VerificationResult {
    val verifiable = entries.filter { it.physicalIndex != null }
    if (verifiable.isEmpty()) return VerificationResult(1.0, emptyList())

    val sampled = if (sampleSize > 0) verifiable.shuffled().take(sampleSize) else verifiable

    val incorrectItems = mutableListOf<IncorrectItem>()
    for (entry in sampled) {
      val page = taggedPages.find { it.pageNumber == entry.physicalIndex } ?: continue
      val prompt = PromptTemplate.render(
        promptProvider, PromptName.VERIFICATION,
        PromptVar.TITLE to entry.title, PromptVar.PAGE_TEXT to page.text
      )
      val response = llmClient.chat(
        listOf(LlmMessage(LlmRole.USER, prompt)),
        tags = listOf("verification")
      )
      val parsed = LlmJsonParser.parseAs(response, VerificationResponse::class.java)
      val answer = parsed?.answer?.trim()?.lowercase() ?: LlmField.NO
      if (answer == LlmField.NO) {
        val reason = parsed?.thinking ?: "Title not found on page"
        incorrectItems.add(IncorrectItem(entry, entry.physicalIndex, reason))
      }
    }

    val accuracy = if (sampled.isEmpty()) 1.0 else (sampled.size - incorrectItems.size).toDouble() / sampled.size
    return VerificationResult(accuracy, incorrectItems)
  }
}
