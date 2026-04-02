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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class LlmStructureDetector(
  private val llmClient: LlmClient,
  private val promptProvider: PromptProvider
) : StructureDetector {

  override suspend fun detect(
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): StructureDetectionResult? {
    val strippedPages = pages.map { page ->
      page.copy(text = HEADER_PREFIX.replace(page.text, ""))
    }
    val taggedPages = PhysicalIndexTagger.tag(strippedPages)

    val groups = PageGrouper.group(taggedPages, config.pageGroupMaxTokens, config.pageGroupOverlap)
    if (groups.isEmpty()) return null

    val concurrency = config.structureDetectionConcurrency.coerceAtLeast(1)
    val semaphore = Semaphore(concurrency)

    val mergedMaps: List<Map<String, Any?>> = if (groups.size == 1) {
      val parsed = semaphore.withPermit { runInit(groups.single()) } ?: return null
      if (parsed.isEmpty()) return null
      parsed
    } else {
      val partials = coroutineScope {
        groups.map { group ->
          async {
            semaphore.withPermit { runInit(group) }
          }
        }.awaitAll()
      }.filterNotNull().filter { it.isNotEmpty() }

      if (partials.isEmpty()) return null

      treeMergePartials(partials, semaphore) ?: return null
    }

    if (mergedMaps.isEmpty()) return null
    return mapsToResult(mergedMaps)
  }

  private suspend fun runInit(group: List<ParsedPage>): List<Map<String, Any?>>? {
    val groupText = group.joinToString("\n") { it.text }
    val initPrompt = PromptTemplate.render(
      promptProvider, PromptName.STRUCTURE_DETECTION_INIT,
      PromptVar.TAGGED_PAGES_TEXT to groupText
    )
    val initResponse = llmClient.chat(
      listOf(LlmMessage(LlmRole.USER, initPrompt)),
      tags = listOf("structure-detection-init")
    )
    return LlmJsonParser.parseList(initResponse)
  }

  private suspend fun treeMergePartials(
    initial: List<List<Map<String, Any?>>>,
    semaphore: Semaphore
  ): List<Map<String, Any?>>? {
    var layer = initial.toMutableList()
    while (layer.size > 1) {
      val chunks = layer.chunked(2)
      val next = coroutineScope {
        chunks.map { pair ->
          async {
            semaphore.withPermit {
              when (pair.size) {
                1 -> pair[0]
                else -> mergeFragments(pair[0], pair[1]) ?: return@withPermit null
              }
            }
          }
        }.awaitAll()
      }
      if (next.any { it == null }) return null
      layer = next.filterNotNull().toMutableList()
    }
    return layer.singleOrNull()
  }

  private suspend fun mergeFragments(
    a: List<Map<String, Any?>>,
    b: List<Map<String, Any?>>
  ): List<Map<String, Any?>>? {
    val prompt = PromptTemplate.render(
      promptProvider, PromptName.STRUCTURE_DETECTION_MERGE,
      PromptVar.PARTIAL_JSON_A to LlmJsonParser.writeValueAsString(a),
      PromptVar.PARTIAL_JSON_B to LlmJsonParser.writeValueAsString(b)
    )
    val response = llmClient.chat(
      listOf(LlmMessage(LlmRole.USER, prompt)),
      tags = listOf("structure-detection-merge")
    )
    return LlmJsonParser.parseList(response)
  }

  private fun mapsToResult(allEntries: List<Map<String, Any?>>): StructureDetectionResult {
    val entries = allEntries.map { item ->
      val physIdx = LlmResponseParser.parsePhysicalIndex(item[LlmField.PHYSICAL_INDEX]?.toString())
      val level = LlmResponseParser.detectLevel(item[LlmField.STRUCTURE]?.toString())
      TocEntry(
        title = item[LlmField.TITLE]?.toString() ?: "",
        pageNumber = null,
        physicalIndex = physIdx,
        level = level,
        structure = item[LlmField.STRUCTURE]?.toString()
      )
    }
    return StructureDetectionResult(
      entries = entries,
      method = IndexingMethod.LLM_DETECTED
    )
  }

  companion object {
    private val HEADER_PREFIX = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
  }
}
