package com.c0x12c.pageindex.core.cache

import com.c0x12c.pageindex.api.RegexPatternCache
import com.c0x12c.pageindex.api.model.DiscoveredPattern

/**
 * In-memory LRU cache for discovered regex patterns.
 *
 * @param maxSize maximum entries before the oldest is evicted. Default: 256.
 */
class InMemoryRegexPatternCache(
  private val maxSize: Int = DEFAULT_MAX_SIZE
) : RegexPatternCache {

  private val store = object : LinkedHashMap<String, List<DiscoveredPattern>>(
    INITIAL_CAPACITY, LOAD_FACTOR, true
  ) {
    override fun removeEldestEntry(
      eldest: MutableMap.MutableEntry<String, List<DiscoveredPattern>>?
    ): Boolean = size > maxSize
  }

  override suspend fun get(fingerprint: String): List<DiscoveredPattern>? =
    synchronized(store) { store[fingerprint] }

  override suspend fun put(fingerprint: String, patterns: List<DiscoveredPattern>) {
    synchronized(store) { store[fingerprint] = patterns }
  }

  companion object {
    internal const val DEFAULT_MAX_SIZE = 256
    private const val INITIAL_CAPACITY = 16
    private const val LOAD_FACTOR = 0.75f
  }
}
