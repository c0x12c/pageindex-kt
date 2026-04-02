package com.c0x12c.pageindex.api.model

/**
 * Configuration for document indexing. All parameters have sensible defaults.
 *
 * Use `IndexingConfig()` for defaults, or override specific values:
 * ```kotlin
 * IndexingConfig(maxNodeTextLength = 12000, summaryMaxTokens = 300)
 * ```
 *
 * @property maxNodeTextLength Maximum character length per tree node before splitting. Larger values
 *   keep more context per node but increase LLM token usage. Default: 8000.
 * @property summaryMaxTokens Token limit for auto-generated node summaries. Default: 200.
 * @property batchSummarySize Number of nodes to summarize in a single LLM call. Default: 10.
 * @property tocScanPages Number of pages from the start to scan for a table of contents. Default: 10.
 * @property minTocLineCount Minimum lines on a page to consider it a TOC page. Default: 3.
 * @property minHeaderCount Minimum markdown headers required for header-based detection. Default: 3.
 * @property verificationSampleSize Number of TOC entries to sample for accuracy verification. Default: 3.
 * @property verificationAccuracyThreshold Fraction of sampled entries that must match (0.0–1.0). Default: 0.7.
 * @property maxFixAttempts Maximum attempts to auto-fix incorrect TOC mappings. Default: 2.
 * @property maxExtractionRetries Maximum retries for LLM-based structure extraction. Default: 2.
 * @property pageGroupMaxTokens Token budget per page group when using LLM structure detection. Default: 4000.
 * @property pageGroupOverlap Number of overlapping pages between adjacent groups. Default: 1.
 * @property fixSearchRadius Pages to search around a TOC entry when fixing page mappings. Default: 3.
 * @property summaryConcurrency Maximum concurrent summary generation requests. Default: 5.
 * @property embeddingConcurrency Maximum concurrent embedding requests. Default: 3.
 * @property structureDetectionConcurrency Maximum concurrent structure detection requests. Default: 5.
 */
data class IndexingConfig(
  val maxNodeTextLength: Int = 8000,
  val summaryMaxTokens: Int = 200,
  val batchSummarySize: Int = 10,
  val tocScanPages: Int = 10,
  val minTocLineCount: Int = 3,
  val minHeaderCount: Int = 3,
  val verificationSampleSize: Int = 3,
  val verificationAccuracyThreshold: Double = 0.7,
  val maxFixAttempts: Int = 2,
  val maxExtractionRetries: Int = 2,
  val pageGroupMaxTokens: Int = 4000,
  val pageGroupOverlap: Int = 1,
  val fixSearchRadius: Int = 3,
  val summaryConcurrency: Int = 5,
  val embeddingConcurrency: Int = 3,
  val structureDetectionConcurrency: Int = 5
) {
  init {
    // Size and token limits
    require(maxNodeTextLength > 0) { "maxNodeTextLength must be positive, got $maxNodeTextLength" }
    require(summaryMaxTokens > 0) { "summaryMaxTokens must be positive, got $summaryMaxTokens" }
    require(pageGroupMaxTokens > 0) { "pageGroupMaxTokens must be positive, got $pageGroupMaxTokens" }

    // Batch and count thresholds
    require(batchSummarySize > 0) { "batchSummarySize must be positive, got $batchSummarySize" }
    require(tocScanPages > 0) { "tocScanPages must be positive, got $tocScanPages" }
    require(minTocLineCount > 0) { "minTocLineCount must be positive, got $minTocLineCount" }
    require(minHeaderCount > 0) { "minHeaderCount must be positive, got $minHeaderCount" }
    require(verificationSampleSize > 0) { "verificationSampleSize must be positive, got $verificationSampleSize" }

    // Accuracy threshold
    require(verificationAccuracyThreshold in 0.0..1.0) {
      "verificationAccuracyThreshold must be between 0.0 and 1.0, got $verificationAccuracyThreshold"
    }

    // Retry and radius limits (zero means disabled)
    require(maxFixAttempts >= 0) { "maxFixAttempts must be non-negative, got $maxFixAttempts" }
    require(maxExtractionRetries >= 0) { "maxExtractionRetries must be non-negative, got $maxExtractionRetries" }
    require(pageGroupOverlap >= 0) { "pageGroupOverlap must be non-negative, got $pageGroupOverlap" }
    require(fixSearchRadius >= 0) { "fixSearchRadius must be non-negative, got $fixSearchRadius" }

    // Concurrency (zero would deadlock semaphores)
    require(summaryConcurrency > 0) { "summaryConcurrency must be positive, got $summaryConcurrency" }
    require(embeddingConcurrency > 0) { "embeddingConcurrency must be positive, got $embeddingConcurrency" }
    require(structureDetectionConcurrency > 0) { "structureDetectionConcurrency must be positive, got $structureDetectionConcurrency" }
  }
}
