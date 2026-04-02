package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.core.cache.InMemoryRegexPatternCache
import com.c0x12c.pageindex.api.model.DiscoveredPattern
import com.c0x12c.pageindex.core.prompt.ResourcePromptProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LlmRegexDiscoveryDetectorTest {

  private val prompts = ResourcePromptProvider()
  private val config = testConfig()

  private val sampleDoc = listOf(
    ParsedPage(1, "CHAPTER 1: Introduction\nSome introductory text here."),
    ParsedPage(2, "CHAPTER 2: Methods\nDescription of methods used."),
    ParsedPage(3, "CHAPTER 3: Results\nThe findings are presented below.")
  )

  private val validPatternJson = """
    [
      {
        "regex": "^CHAPTER\\s+\\d+:\\s+(.+)$",
        "level": 1,
        "description": "Chapter headers"
      }
    ]
  """.trimIndent()

  @Test
  fun `detect - discovers patterns and applies to document`() = runTest {
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, validPatternJson)
    val detector = LlmRegexDiscoveryDetector(llm, prompts)

    val result = detector.detect(sampleDoc, config)

    assertNotNull(result)
    assertEquals(IndexingMethod.REGEX_DISCOVERED, result?.method)
    assertEquals(3, result?.entries?.size)
    assertEquals("Introduction", result?.entries?.get(0)?.title)
    assertEquals("Methods", result?.entries?.get(1)?.title)
    assertEquals("Results", result?.entries?.get(2)?.title)
  }

  @Test
  fun `detect - cache hit skips LLM call`() = runTest {
    val cache = InMemoryRegexPatternCache()
    val fingerprint = LlmRegexDiscoveryDetector.fingerprint(sampleDoc)
    cache.put(fingerprint, listOf(
      DiscoveredPattern("^CHAPTER\\s+\\d+:\\s+(.+)$", 1, "Chapter headers")
    ))

    val llm = FakeLlmClient()
    val detector = LlmRegexDiscoveryDetector(llm, prompts, cache)

    val result = detector.detect(sampleDoc, config)

    assertNotNull(result)
    assertEquals(3, result?.entries?.size)
    assertEquals(0, llm.callCount())
  }

  @Test
  fun `detect - cache miss calls LLM and caches result`() = runTest {
    val cache = InMemoryRegexPatternCache()
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, validPatternJson)
    val detector = LlmRegexDiscoveryDetector(llm, prompts, cache)

    detector.detect(sampleDoc, config)

    assertEquals(1, llm.callCount())
    val fingerprint = LlmRegexDiscoveryDetector.fingerprint(sampleDoc)
    assertNotNull(cache.get(fingerprint))
  }

  @Test
  fun `detect - invalid regex is discarded`() = runTest {
    val json = """[{"regex": "([unclosed", "level": 1, "description": "bad"}]"""
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, json)
    val detector = LlmRegexDiscoveryDetector(llm, prompts)

    assertNull(detector.detect(sampleDoc, config))
  }

  @Test
  fun `detect - pattern with wrong group count is discarded`() = runTest {
    val json = """[{"regex": "^CHAPTER\\s+\\d+", "level": 1, "description": "no groups"}]"""
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, json)
    val detector = LlmRegexDiscoveryDetector(llm, prompts)

    assertNull(detector.detect(sampleDoc, config))
  }

  @Test
  fun `detect - overly broad pattern is discarded`() = runTest {
    val json = """[{"regex": "^(.+)$", "level": 1, "description": "matches everything"}]"""
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, json)
    val detector = LlmRegexDiscoveryDetector(llm, prompts)

    assertNull(detector.detect(sampleDoc, config))
  }

  @Test
  fun `detect - empty array returns null`() = runTest {
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, "[]")
    val detector = LlmRegexDiscoveryDetector(llm, prompts)

    assertNull(detector.detect(sampleDoc, config))
  }

  @Test
  fun `detect - unparseable response returns null`() = runTest {
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, "not valid json at all")
    val detector = LlmRegexDiscoveryDetector(llm, prompts)

    assertNull(detector.detect(sampleDoc, config))
  }

  @Test
  fun `detect - returns null when entries below threshold`() = runTest {
    val oneChapterDoc = listOf(
      ParsedPage(1, "CHAPTER 1: Introduction\nLots of text but only one chapter.")
    )
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, validPatternJson)
    val detector = LlmRegexDiscoveryDetector(llm, prompts)

    assertNull(detector.detect(oneChapterDoc, config))
  }

  @Test
  fun `detect - uses correct LLM tag`() = runTest {
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, validPatternJson)
    val detector = LlmRegexDiscoveryDetector(llm, prompts)

    detector.detect(sampleDoc, config)

    assertEquals(1, llm.callCount(LlmRegexDiscoveryDetector.LLM_TAG))
  }

  @Test
  fun `detect - ignores matches inside code blocks`() = runTest {
    val docWithCode = listOf(
      ParsedPage(1, "CHAPTER 1: Introduction\nSome text."),
      ParsedPage(2, "```\nCHAPTER 2: In Code Block\n```\nCHAPTER 3: Results\nMore text.")
    )
    val llm = FakeLlmClient().onTag(LlmRegexDiscoveryDetector.LLM_TAG, validPatternJson)
    val detector = LlmRegexDiscoveryDetector(llm, prompts)

    val result = detector.detect(docWithCode, config)

    assertNotNull(result)
    assertEquals(2, result?.entries?.size)
    assertEquals("Introduction", result?.entries?.get(0)?.title)
    assertEquals("Results", result?.entries?.get(1)?.title)
  }

  @Test
  fun `detect - cached empty list returns null without LLM call`() = runTest {
    val cache = InMemoryRegexPatternCache()
    val fingerprint = LlmRegexDiscoveryDetector.fingerprint(sampleDoc)
    cache.put(fingerprint, emptyList())

    val llm = FakeLlmClient()
    val detector = LlmRegexDiscoveryDetector(llm, prompts, cache)

    assertNull(detector.detect(sampleDoc, config))
    assertEquals(0, llm.callCount())
  }

  @Test
  fun `fingerprint - same content produces same hash`() {
    val pages1 = listOf(ParsedPage(1, "Hello"), ParsedPage(2, "World"))
    val pages2 = listOf(ParsedPage(1, "Hello"), ParsedPage(2, "World"))

    assertEquals(
      LlmRegexDiscoveryDetector.fingerprint(pages1),
      LlmRegexDiscoveryDetector.fingerprint(pages2)
    )
  }

  @Test
  fun `fingerprint - different content produces different hash`() {
    val pages1 = listOf(ParsedPage(1, "Hello"))
    val pages2 = listOf(ParsedPage(1, "Different"))

    assert(
      LlmRegexDiscoveryDetector.fingerprint(pages1) !=
        LlmRegexDiscoveryDetector.fingerprint(pages2)
    )
  }
}
