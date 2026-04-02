package com.c0x12c.pageindex.core.detector

import com.c0x12c.pageindex.api.StructureDetector
import com.c0x12c.pageindex.api.model.IndexingConfig
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.StructureDetectionResult
import org.slf4j.LoggerFactory

class ChainedStructureDetector(
  private val detectors: List<StructureDetector>
) : StructureDetector {

  private val logger = LoggerFactory.getLogger(ChainedStructureDetector::class.java)

  override suspend fun detect(
    pages: List<ParsedPage>,
    config: IndexingConfig
  ): StructureDetectionResult? {
    for (detector in detectors) {
      val name = detector::class.simpleName
      logger.info("Trying detector: $name")
      val result = detector.detect(pages, config)
      if (result != null) {
        logger.info("Detector $name matched, method=${result.method}, entries=${result.entries.size}")
        return result
      }
      logger.info("Detector $name returned null, trying next")
    }
    return null
  }
}
