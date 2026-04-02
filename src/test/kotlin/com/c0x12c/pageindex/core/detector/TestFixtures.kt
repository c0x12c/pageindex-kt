package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.model.IndexingConfig

fun testConfig(
  maxNodeTextLength: Int = 5000,
  tocScanPages: Int = 20,
  minTocLineCount: Int = 3,
  minHeaderCount: Int = 2,
  pageGroupMaxTokens: Int = 2000,
  pageGroupOverlap: Int = 1,
  summaryConcurrency: Int = 1
) = IndexingConfig(
  maxNodeTextLength = maxNodeTextLength,
  summaryMaxTokens = 500,
  batchSummarySize = 5,
  tocScanPages = tocScanPages,
  minTocLineCount = minTocLineCount,
  minHeaderCount = minHeaderCount,
  verificationSampleSize = 5,
  verificationAccuracyThreshold = 0.8,
  maxFixAttempts = 3,
  maxExtractionRetries = 5,
  pageGroupMaxTokens = pageGroupMaxTokens,
  pageGroupOverlap = pageGroupOverlap,
  fixSearchRadius = 5,
  summaryConcurrency = summaryConcurrency,
  structureDetectionConcurrency = 5
)
