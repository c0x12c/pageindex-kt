package com.c0x12c.pageindex.benchmark.dataset

import com.c0x12c.pageindex.benchmark.model.BenchmarkQuestion

/**
 * Interface for loading benchmark datasets.
 *
 * Implement this to add a new dataset:
 * 1. Create a class that implements BenchmarkDataset
 * 2. Load questions from your data source in load()
 * 3. Return unique document identifiers in documentIds()
 *
 * See FinanceBenchDataset for an example.
 */
interface BenchmarkDataset {
    val name: String
    fun load(): List<BenchmarkQuestion>
    fun documentIds(): Set<String>
}
