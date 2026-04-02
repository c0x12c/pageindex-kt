package com.c0x12c.pageindex.core.cache

import com.c0x12c.pageindex.api.model.DiscoveredPattern
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InMemoryRegexPatternCacheTest {

  private val cache = InMemoryRegexPatternCache()

  @Test
  fun `get returns null for unknown key`() = runTest {
    assertNull(cache.get("unknown"))
  }

  @Test
  fun `put then get returns stored patterns`() = runTest {
    val patterns = listOf(
      DiscoveredPattern("^CHAPTER (.+)$", 1, "chapters")
    )
    cache.put("key1", patterns)

    assertEquals(patterns, cache.get("key1"))
  }

  @Test
  fun `put with same key overwrites`() = runTest {
    val old = listOf(DiscoveredPattern("^old (.+)$", 1))
    val new = listOf(DiscoveredPattern("^new (.+)$", 2))

    cache.put("key1", old)
    cache.put("key1", new)

    assertEquals(new, cache.get("key1"))
  }

  @Test
  fun `evicts oldest entry when maxSize exceeded`() = runTest {
    val small = InMemoryRegexPatternCache(maxSize = 2)
    val p1 = listOf(DiscoveredPattern("^a (.+)$", 1))
    val p2 = listOf(DiscoveredPattern("^b (.+)$", 1))
    val p3 = listOf(DiscoveredPattern("^c (.+)$", 1))

    small.put("k1", p1)
    small.put("k2", p2)
    small.put("k3", p3)

    assertNull(small.get("k1"))
    assertNotNull(small.get("k2"))
    assertNotNull(small.get("k3"))
  }

  @Test
  fun `LRU access keeps recently read entry`() = runTest {
    val small = InMemoryRegexPatternCache(maxSize = 2)
    val p1 = listOf(DiscoveredPattern("^a (.+)$", 1))
    val p2 = listOf(DiscoveredPattern("^b (.+)$", 1))
    val p3 = listOf(DiscoveredPattern("^c (.+)$", 1))

    small.put("k1", p1)
    small.put("k2", p2)
    small.get("k1") // touch k1 so k2 becomes oldest
    small.put("k3", p3)

    assertNotNull(small.get("k1"))
    assertNull(small.get("k2"))
    assertNotNull(small.get("k3"))
  }
}
