package com.c0x12c.pageindex.core.indexer

import arrow.core.Either
import com.c0x12c.pageindex.api.NodeEmbeddingService
import com.c0x12c.pageindex.api.model.DocumentTree
import com.c0x12c.pageindex.api.model.IndexingMethod
import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.StructureDetectionResult
import com.c0x12c.pageindex.api.model.TocEntry
import com.c0x12c.pageindex.core.detector.FixedDetector
import com.c0x12c.pageindex.core.detector.NullDetector
import com.c0x12c.pageindex.core.detector.testConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DefaultTreeIndexBuilderTest {

  private fun pages(count: Int = 5) = (1..count).map {
    ParsedPage(pageNumber = it, text = "Page $it content")
  }

  private fun flatEntries(count: Int = 3) = (1..count).map { i ->
    TocEntry(title = "Section $i", pageNumber = i, physicalIndex = i, level = 1)
  }

  private fun flatDetection(count: Int = 3) = StructureDetectionResult(
    entries = flatEntries(count),
    method = IndexingMethod.FLAT_PAGES
  )

  private val fakeEmbeddingService = object : NodeEmbeddingService {
    override suspend fun embed(texts: List<String>): List<FloatArray> =
      texts.map { FloatArray(4) { 0.1f } }
    override suspend fun embedSingle(text: String): FloatArray = FloatArray(4) { 0.1f }
  }

  private fun builder(
    detector: com.c0x12c.pageindex.api.StructureDetector
  ) = DefaultTreeIndexBuilder(
    structureDetector = detector,
    embeddingService = fakeEmbeddingService
  )

  @Test
  fun `buildIndex - returns error for empty pages`() = runTest {
    val b = builder(FixedDetector(flatDetection()))

    val result = b.buildIndex(UUID.randomUUID(), UUID.randomUUID(), emptyList(), testConfig())

    assertTrue(result is Either.Left)
  }

  @Test
  fun `buildIndex - returns error when structure detection fails`() = runTest {
    val b = builder(NullDetector())

    val result = b.buildIndex(UUID.randomUUID(), UUID.randomUUID(), pages(), testConfig())

    assertTrue(result is Either.Left)
    val error = (result as Either.Left).value
    assertEquals("PAGE_INDEX_STRUCTURE_FAILED", error.code)
  }

  @Test
  fun `buildIndex - full pipeline with flat pages detection`() = runTest {
    val b = builder(FixedDetector(flatDetection(3)))

    val result = b.buildIndex(UUID.randomUUID(), UUID.randomUUID(), pages(5), testConfig())

    assertTrue(result is Either.Right)
    val tree = (result as Either.Right).value as DocumentTree
    assertEquals(3, tree.rootNodes.size)
  }

  @Test
  fun `buildIndex - splits oversized nodes`() = runTest {
    val longText = "A".repeat(200)
    val entries = listOf(
      TocEntry(title = "Big Section", pageNumber = 1, physicalIndex = 1, level = 1)
    )
    val detection = StructureDetectionResult(entries = entries, method = IndexingMethod.FLAT_PAGES)
    val pageLongText = pages(3).map { it.copy(text = longText) }
    val config = testConfig(maxNodeTextLength = 100)

    val b = builder(FixedDetector(detection))

    val result = b.buildIndex(UUID.randomUUID(), UUID.randomUUID(), pageLongText, config)

    assertTrue(result is Either.Right)
    val tree = (result as Either.Right).value as DocumentTree
    val root = tree.rootNodes.first()
    assertTrue(root.children.isNotEmpty())
  }

  @Test
  fun `buildIndex - metadata has correct totalNodes totalPages maxDepth`() = runTest {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Sec1.1", pageNumber = 2, physicalIndex = 2, level = 2),
      TocEntry(title = "Ch2", pageNumber = 4, physicalIndex = 4, level = 1)
    )
    val detection = StructureDetectionResult(entries = entries, method = IndexingMethod.FLAT_PAGES)
    val b = builder(FixedDetector(detection))

    val result = b.buildIndex(UUID.randomUUID(), UUID.randomUUID(), pages(5), testConfig())

    assertTrue(result is Either.Right)
    val tree = (result as Either.Right).value as DocumentTree
    val metadata = tree.metadata
    assertEquals(3, metadata.totalNodes)
    assertEquals(5, metadata.totalPages)
    assertEquals(2, metadata.maxDepth)
  }

  @Test
  fun `buildIndex - wires parent references on completed tree`() = runTest {
    val entries = listOf(
      TocEntry(title = "Ch1", pageNumber = 1, physicalIndex = 1, level = 1),
      TocEntry(title = "Sec1.1", pageNumber = 2, physicalIndex = 2, level = 2),
      TocEntry(title = "Ch2", pageNumber = 3, physicalIndex = 3, level = 1)
    )
    val detection = StructureDetectionResult(entries = entries, method = IndexingMethod.FLAT_PAGES)
    val b = builder(FixedDetector(detection))

    val result = b.buildIndex(UUID.randomUUID(), UUID.randomUUID(), pages(5), testConfig())

    assertTrue(result is Either.Right)
    val tree = (result as Either.Right).value as DocumentTree
    assertNull(tree.rootNodes[0].parent)
    assertNull(tree.rootNodes[1].parent)
    assertNotNull(tree.rootNodes[0].children[0].parent)
    assertEquals(tree.rootNodes[0], tree.rootNodes[0].children[0].parent)
  }
}
