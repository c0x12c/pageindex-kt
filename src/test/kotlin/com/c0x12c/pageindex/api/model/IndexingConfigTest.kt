package com.c0x12c.pageindex.api.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class IndexingConfigTest {

  @Test
  fun `default config is valid`() {
    assertDoesNotThrow { IndexingConfig() }
  }

  @Test
  fun `custom valid config is accepted`() {
    assertDoesNotThrow {
      IndexingConfig(
        maxNodeTextLength = 12000,
        summaryMaxTokens = 300,
        summaryConcurrency = 10,
        verificationAccuracyThreshold = 0.5
      )
    }
  }

  @Test
  fun `boundary values are accepted`() {
    assertDoesNotThrow {
      IndexingConfig(
        verificationAccuracyThreshold = 0.0,
        maxFixAttempts = 0,
        maxExtractionRetries = 0,
        pageGroupOverlap = 0,
        fixSearchRadius = 0
      )
    }
    assertDoesNotThrow {
      IndexingConfig(verificationAccuracyThreshold = 1.0)
    }
  }

  @Test
  fun `rejects zero maxNodeTextLength`() {
    val ex = assertThrows<IllegalArgumentException> {
      IndexingConfig(maxNodeTextLength = 0)
    }
    assertEquals("maxNodeTextLength must be positive, got 0", ex.message)
  }

  @Test
  fun `rejects negative summaryMaxTokens`() {
    val ex = assertThrows<IllegalArgumentException> {
      IndexingConfig(summaryMaxTokens = -1)
    }
    assertEquals("summaryMaxTokens must be positive, got -1", ex.message)
  }

  @Test
  fun `rejects zero summaryConcurrency`() {
    val ex = assertThrows<IllegalArgumentException> {
      IndexingConfig(summaryConcurrency = 0)
    }
    assertEquals("summaryConcurrency must be positive, got 0", ex.message)
  }

  @Test
  fun `rejects zero embeddingConcurrency`() {
    val ex = assertThrows<IllegalArgumentException> {
      IndexingConfig(embeddingConcurrency = 0)
    }
    assertEquals("embeddingConcurrency must be positive, got 0", ex.message)
  }

  @Test
  fun `rejects zero structureDetectionConcurrency`() {
    val ex = assertThrows<IllegalArgumentException> {
      IndexingConfig(structureDetectionConcurrency = 0)
    }
    assertEquals("structureDetectionConcurrency must be positive, got 0", ex.message)
  }

  @Test
  fun `rejects threshold above 1`() {
    val ex = assertThrows<IllegalArgumentException> {
      IndexingConfig(verificationAccuracyThreshold = 1.1)
    }
    assertEquals(
      "verificationAccuracyThreshold must be between 0.0 and 1.0, got 1.1",
      ex.message
    )
  }

  @Test
  fun `rejects negative threshold`() {
    val ex = assertThrows<IllegalArgumentException> {
      IndexingConfig(verificationAccuracyThreshold = -0.1)
    }
    assertEquals(
      "verificationAccuracyThreshold must be between 0.0 and 1.0, got -0.1",
      ex.message
    )
  }

  @Test
  fun `rejects negative maxFixAttempts`() {
    val ex = assertThrows<IllegalArgumentException> {
      IndexingConfig(maxFixAttempts = -1)
    }
    assertEquals("maxFixAttempts must be non-negative, got -1", ex.message)
  }

  @Test
  fun `rejects negative pageGroupOverlap`() {
    val ex = assertThrows<IllegalArgumentException> {
      IndexingConfig(pageGroupOverlap = -1)
    }
    assertEquals("pageGroupOverlap must be non-negative, got -1", ex.message)
  }
}
