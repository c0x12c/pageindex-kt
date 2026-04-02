package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.LlmClient
import com.c0x12c.pageindex.api.PromptProvider
import com.c0x12c.pageindex.api.RegexPatternCache
import com.c0x12c.pageindex.api.StructureDetector
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.LlmMessage
import com.c0x12c.pageindex.api.model.LlmRole
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.PromptName
import com.c0x12c.pageindex.api.model.StructureDetectionResult
import com.c0x12c.pageindex.api.model.TocEntry
import com.c0x12c.pageindex.api.model.DiscoveredPattern
import com.c0x12c.pageindex.core.model.PromptVar
import com.c0x12c.pageindex.core.prompt.PromptTemplate
import com.c0x12c.pageindex.core.util.LlmJsonParser
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Uses a single LLM call to discover regex patterns for section headers, then applies
 * them deterministically. Caches discovered patterns by content fingerprint so repeated
 * documents from the same source skip the LLM call.
 */
class LlmRegexDiscoveryDetector(
  private val llmClient: LlmClient,
  private val promptProvider: PromptProvider,
  private val cache: RegexPatternCache? = null,
  private val samplePageCount: Int = DEFAULT_SAMPLE_PAGES
) : StructureDetector {

  private val log = LoggerFactory.getLogger(LlmRegexDiscoveryDetector::class.java)

  override suspend fun detect(
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): StructureDetectionResult? {
    if (pages.isEmpty()) return null

    val samplePages = pages.take(samplePageCount)
    val fingerprint = fingerprint(samplePages)

    // Check cache
    val cached = cache?.get(fingerprint)
    if (cached != null) {
      log.debug("Cache hit for fingerprint {}", fingerprint.take(FINGERPRINT_LOG_LENGTH))
      if (cached.isEmpty()) return null
      return applyPatterns(cached, pages, config)
    }

    // Discover patterns via LLM
    val sampleText = samplePages.joinToString(PAGE_SEPARATOR) { page ->
      "$PAGE_HEADER_PREFIX${page.pageNumber} $PAGE_HEADER_SUFFIX\n${page.text}"
    }

    val prompt = PromptTemplate.render(
      promptProvider, PromptName.REGEX_DISCOVERY,
      PromptVar.SAMPLE_PAGES_TEXT to sampleText
    )

    val response = try {
      llmClient.chat(
        listOf(LlmMessage(LlmRole.USER, prompt)),
        tags = listOf(LLM_TAG)
      )
    } catch (e: Exception) {
      log.warn("LLM call failed for regex discovery: {}", e.message)
      return null
    }

    // Parse and validate
    val rawPatterns = LlmJsonParser.parseListAs(response, DiscoveredPattern::class.java)
    if (rawPatterns.isNullOrEmpty()) {
      log.debug("No patterns returned by LLM")
      cache?.put(fingerprint, emptyList())
      return null
    }

    val sampleLines = samplePages.flatMap { it.text.lines() }.filter { it.isNotBlank() }
    val validated = validatePatterns(rawPatterns, sampleLines)

    // Cache (even empty — prevents re-calling LLM for same doc)
    cache?.put(fingerprint, validated)

    if (validated.isEmpty()) {
      log.debug("All discovered patterns failed validation")
      return null
    }

    return applyPatterns(validated, pages, config)
  }

  private fun validatePatterns(
    patterns: List<DiscoveredPattern>,
    sampleLines: List<String>
  ): List<DiscoveredPattern> {
    val totalNonBlank = sampleLines.size
    return patterns.filter { pattern ->
      val compiled = tryCompile(pattern.regex) ?: return@filter false

      if (compiled.matcher("").groupCount() != REQUIRED_GROUP_COUNT) {
        log.debug("Discarding pattern with wrong group count: {}", pattern.regex)
        return@filter false
      }

      val matchCount = sampleLines.count { compiled.matcher(it).find() }

      if (matchCount == 0) {
        log.debug("Discarding pattern with zero matches: {}", pattern.regex)
        return@filter false
      }

      if (totalNonBlank > 0 && matchCount.toDouble() / totalNonBlank > MAX_MATCH_RATIO) {
        log.debug("Discarding overly broad pattern ({}/{}): {}", matchCount, totalNonBlank, pattern.regex)
        return@filter false
      }

      true
    }
  }

  private fun applyPatterns(
    patterns: List<DiscoveredPattern>,
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): StructureDetectionResult? {
    val compiled = patterns.mapNotNull { pattern ->
      tryCompile(pattern.regex)?.let { it to pattern.level }
    }.sortedBy { it.second }

    if (compiled.isEmpty()) return null

    val entries = mutableListOf<TocEntry>()
    var inCodeBlock = false

    for (page in pages) {
      for (line in page.text.lines()) {
        if (line.trimStart().startsWith(CODE_FENCE)) {
          inCodeBlock = !inCodeBlock
          continue
        }
        if (inCodeBlock) continue

        for ((regex, level) in compiled) {
          val match = regex.matcher(line)
          if (match.find()) {
            val title = match.group(TITLE_GROUP).trim()
            if (title.isNotBlank()) {
              entries.add(TocEntry(
                title = title,
                pageNumber = null,
                physicalIndex = page.pageNumber,
                level = level
              ))
            }
            break // first matching pattern wins
          }
        }
      }
    }

    if (entries.size < config.minHeaderCount) {
      log.debug("Only {} entries found, below threshold {}", entries.size, config.minHeaderCount)
      return null
    }

    return StructureDetectionResult(
      entries = entries,
      method = IndexingMethod.REGEX_DISCOVERED
    )
  }

  private fun tryCompile(regex: String): Pattern? {
    return try {
      Pattern.compile(regex, Pattern.MULTILINE)
    } catch (_: PatternSyntaxException) {
      log.debug("Invalid regex: {}", regex)
      null
    }
  }

  companion object {
    private const val DEFAULT_SAMPLE_PAGES = 5
    private const val REQUIRED_GROUP_COUNT = 1
    private const val MAX_MATCH_RATIO = 0.5
    private const val FINGERPRINT_LOG_LENGTH = 12
    private const val CODE_FENCE = "```"
    private const val PAGE_SEPARATOR = "\n\n"
    private const val PAGE_HEADER_PREFIX = "--- Page"
    private const val PAGE_HEADER_SUFFIX = "---"
    private const val TITLE_GROUP = 1
    internal const val LLM_TAG = "regex-discovery"

    internal fun fingerprint(pages: List<ParsedPage>): String {
      val digest = MessageDigest.getInstance("SHA-256")
      pages.forEach { digest.update(it.text.toByteArray()) }
      return digest.digest().joinToString("") { "%02x".format(it) }
    }
  }
}
