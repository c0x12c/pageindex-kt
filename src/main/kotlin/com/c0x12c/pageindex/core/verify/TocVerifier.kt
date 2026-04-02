package com.c0x12c.pageindex.core.verify

import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.PromptProvider
import com.c0x12c.pageindex.core.model.IncorrectItem
import com.c0x12c.pageindex.core.model.LlmField
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.PromptName
import com.c0x12c.pageindex.core.model.PromptVar
import com.c0x12c.pageindex.api.model.TocEntry
import com.c0x12c.pageindex.core.model.VerificationResult
import com.c0x12c.pageindex.core.prompt.PromptTemplate
import com.c0x12c.pageindex.core.util.LlmJsonParser
import com.c0x12c.pageindex.core.util.response.VerificationResponse

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
