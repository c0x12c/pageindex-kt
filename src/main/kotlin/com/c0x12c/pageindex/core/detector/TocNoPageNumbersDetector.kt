package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.PromptProvider
import com.c0x12c.pageindex.api.StructureDetector
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.core.model.LlmField
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.PromptName
import com.c0x12c.pageindex.core.model.PromptVar
import com.c0x12c.pageindex.api.model.StructureDetectionResult
import com.c0x12c.pageindex.api.model.TocEntry
import com.c0x12c.pageindex.core.prompt.PromptTemplate
import com.c0x12c.pageindex.core.util.LlmJsonParser
import com.c0x12c.pageindex.core.util.LlmResponseParser
import com.c0x12c.pageindex.core.util.PageGrouper
import com.c0x12c.pageindex.core.util.PhysicalIndexTagger

class TocNoPageNumbersDetector(
  private val llmClient: LlmClient,
  private val promptProvider: PromptProvider
) : StructureDetector {

  private val dotLeaders = Regex("\\.{5,}")

  override suspend fun detect(
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): StructureDetectionResult? {
    val tocPageIndices = findTocPages(pages, config)
    if (tocPageIndices.isEmpty()) return null

    val tocPages = tocPageIndices.map { pages[it] }
    val rawTocText = tocPages.joinToString("\n") { it.text }

    val extractedToc = extractToc(rawTocText) ?: return null

    val completeToc = ensureComplete(rawTocText, extractedToc, config.maxExtractionRetries)

    val tocItems = transformToJson(completeToc) ?: return null

    val hasPageNumbers = checkHasPageNumbers(completeToc)
    if (hasPageNumbers) {
      return null
    }

    val taggedPages = PhysicalIndexTagger.tag(pages)
    val groups = PageGrouper.group(taggedPages, config.pageGroupMaxTokens, config.pageGroupOverlap)
    val entries = matchTitlesToPages(tocItems, groups)

    return StructureDetectionResult(
      entries = entries,
      method = IndexingMethod.TOC_WITHOUT_PAGES,
      rawTocText = rawTocText
    )
  }

  private suspend fun findTocPages(
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): List<Int> {
    val scanPages = pages.take(config.tocScanPages)
    val tocIndices = mutableListOf<Int>()
    var foundToc = false

    for (i in scanPages.indices) {
      val prompt = PromptTemplate.render(
        promptProvider, PromptName.TOC_DETECTION,
        PromptVar.PAGE_TEXT to scanPages[i].text
      )
      val response = llmClient.chat(
        listOf(LlmMessage(LlmRole.USER, prompt)),
        tags = listOf("toc-detection")
      )
      val parsed = LlmJsonParser.parseMap(response)
      val isToc = parsed?.get(LlmField.TOC_DETECTED)?.toString()?.lowercase() == LlmField.YES

      if (isToc) {
        tocIndices.add(i)
        foundToc = true
      } else if (foundToc) {
        break
      }
    }
    return tocIndices
  }

  private suspend fun extractToc(rawTocText: String): String? {
    val cleaned = cleanDotLeaders(rawTocText)
    val prompt = PromptTemplate.render(
      promptProvider, PromptName.TOC_EXTRACTION,
      PromptVar.TOC_TEXT to cleaned
    )
    val response = llmClient.chat(
      listOf(LlmMessage(LlmRole.USER, prompt)),
      tags = listOf("toc-extraction")
    )
    return response.takeIf { it.isNotBlank() }
  }

  private suspend fun ensureComplete(
    rawTocText: String,
    extractedToc: String,
    maxRetries: Int
  ): String {
    var current = extractedToc

    repeat(maxRetries) {
      val checkPrompt = PromptTemplate.render(
        promptProvider, PromptName.TOC_COMPLETENESS_CHECK,
        PromptVar.RAW_TOC_TEXT to rawTocText,
        PromptVar.EXTRACTED_TOC_TEXT to current
      )
      val checkResponse = llmClient.chat(
        listOf(LlmMessage(LlmRole.USER, checkPrompt)),
        tags = listOf("toc-completeness")
      )
      val parsed = LlmJsonParser.parseMap(checkResponse)
      val isComplete = parsed?.get(LlmField.COMPLETED)?.toString()?.lowercase() == LlmField.YES
      if (isComplete) return current

      val cleaned = cleanDotLeaders(rawTocText)
      val extractionPrompt = PromptTemplate.render(
        promptProvider, PromptName.TOC_EXTRACTION,
        PromptVar.TOC_TEXT to cleaned
      )
      val continueResponse = llmClient.chat(
        listOf(
          LlmMessage(LlmRole.USER, extractionPrompt),
          LlmMessage(LlmRole.ASSISTANT, current),
          LlmMessage(LlmRole.USER, "Please continue the extraction.")
        ),
        tags = listOf("toc-extraction-continue")
      )
      current = current + "\n" + continueResponse
    }
    return current
  }

  private suspend fun transformToJson(
    tocText: String
  ): List<Map<String, Any?>>? {
    val prompt = PromptTemplate.render(
      promptProvider, PromptName.TOC_TRANSFORM,
      PromptVar.TOC_TEXT to tocText
    )
    val response = llmClient.chat(
      listOf(LlmMessage(LlmRole.USER, prompt)),
      tags = listOf("toc-transform")
    )
    val parsed = LlmJsonParser.parseMap(response) ?: return null
    val rawList = parsed[LlmField.TABLE_OF_CONTENTS] as? List<*> ?: return null
    return rawList.filterIsInstance<Map<String, Any?>>()
  }

  private suspend fun checkHasPageNumbers(tocText: String): Boolean {
    val prompt = PromptTemplate.render(
      promptProvider, PromptName.PAGE_INDEX_DETECTION,
      PromptVar.TOC_CONTENT to tocText
    )
    val response = llmClient.chat(
      listOf(LlmMessage(LlmRole.USER, prompt)),
      tags = listOf("page-index-detection")
    )
    val parsed = LlmJsonParser.parseMap(response)
    return parsed?.get(LlmField.PAGE_INDEX_GIVEN)?.toString()?.lowercase() == LlmField.YES
  }

  private suspend fun matchTitlesToPages(
    tocItems: List<Map<String, Any?>>,
    groups: List<List<ParsedPage>>
  ): List<TocEntry> {
    var currentStructure = tocItems

    for (group in groups) {
      val pagesText = group.joinToString("\n") { it.text }
      val structureJson = LlmJsonParser.writeValueAsString(currentStructure)
      val prompt = PromptTemplate.render(
        promptProvider, PromptName.TOC_PAGE_MATCHING,
        PromptVar.STRUCTURE_JSON to structureJson,
        PromptVar.PAGES_TEXT to pagesText
      )
      val response = llmClient.chat(
        listOf(LlmMessage(LlmRole.USER, prompt)),
        tags = listOf("toc-page-matching")
      )
      val parsed = LlmJsonParser.parseList(response)
      if (parsed != null) {
        currentStructure = parsed
      }
    }

    return currentStructure.map { item ->
      val physIdx = LlmResponseParser.parsePhysicalIndex(item[LlmField.PHYSICAL_INDEX]?.toString())
      val level = LlmResponseParser.detectLevel(item[LlmField.STRUCTURE]?.toString())
      TocEntry(
        title = item[LlmField.TITLE]?.toString() ?: "",
        pageNumber = (item[LlmField.PAGE] as? Number)?.toInt(),
        physicalIndex = physIdx,
        level = level,
        structure = item[LlmField.STRUCTURE]?.toString()
      )
    }
  }

  private fun cleanDotLeaders(text: String): String = text.replace(dotLeaders, ": ")
}
