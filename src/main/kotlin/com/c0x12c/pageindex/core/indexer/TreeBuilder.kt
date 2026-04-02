package com.c0x12c.pageindex.core.indexer

import com.c0x12c.pageindex.api.model.ParsedPage
import com.c0x12c.pageindex.api.model.TocEntry
import com.c0x12c.pageindex.api.model.TreeNode

object TreeBuilder {
  fun fromEntries(
    entries: List<TocEntry>,
    pages: List<ParsedPage>,
    lastContentPage: Int? = null
  ): List<TreeNode> {
    if (entries.isEmpty()) return emptyList()

    val pagesByNumber = pages.associateBy { it.pageNumber }
    val sorted = entries.sortedBy { it.physicalIndex ?: it.pageNumber ?: Int.MAX_VALUE }
    // lastContentPage caps the final section's end at the last non-structural page
    // (e.g. excludes a TOC that appears at the end of the document).
    val totalPages = lastContentPage ?: pages.maxOfOrNull { it.pageNumber } ?: 0

    val flatNodes = sorted.mapIndexed { idx, entry ->
      val startPage = entry.physicalIndex ?: entry.pageNumber ?: 1
      // Use the next entry at the same or higher level (next sibling or uncle) to determine
      // the end of this section. Using the immediate next entry would give parents the wrong
      // (too small) range when a child starts on the same page as the parent.
      val nextSameLevelIdx = (idx + 1 until sorted.size).firstOrNull { sorted[it].level <= entry.level }
      val endPage = if (nextSameLevelIdx != null) {
        val nextStart = sorted[nextSameLevelIdx].physicalIndex ?: sorted[nextSameLevelIdx].pageNumber ?: startPage
        (nextStart - 1).coerceAtLeast(startPage)
      } else {
        totalPages
      }
      val text = (startPage..endPage)
        .mapNotNull { pagesByNumber[it]?.text }
        .joinToString("\n")

      TreeNode(
        nodeId = "",
        title = entry.title,
        level = entry.level,
        startPage = startPage,
        endPage = endPage,
        text = text.ifEmpty { null }
      )
    }

    val roots = clearParentText(buildHierarchy(flatNodes))

    // Capture pages before the first section (cover page, TOC, preamble, etc.) as a
    // synthetic front-matter node so no content is silently dropped.
    // The node is created whenever the page range is non-empty, even if those pages
    // produced no extractable text (e.g. scanned image pages) — the structural slot
    // is preserved so the LLM can still access the node if text becomes available.
    val firstStartPage = sorted.firstOrNull()?.let { it.physicalIndex ?: it.pageNumber } ?: 1
    val frontMatter = if (firstStartPage > 1) {
      val text = (1 until firstStartPage)
        .mapNotNull { pagesByNumber[it]?.text }
        .joinToString("\n")
      listOf(
        TreeNode(
          nodeId = "",
          title = "Front Matter",
          level = 1,
          startPage = 1,
          endPage = firstStartPage - 1,
          text = text.ifEmpty { null }
        )
      )
    } else {
      emptyList()
    }

    return assignNodeIds(frontMatter + roots)
  }

  // Only leaf nodes (no children) carry raw text. Parent nodes are summarized from their
  // children, so storing redundant full text in parents wastes space and causes embedding
  // overlap during hybrid retrieval.
  private fun clearParentText(nodes: List<TreeNode>): List<TreeNode> =
    nodes.map { node ->
      val updatedChildren = clearParentText(node.children)
      if (node.children.isNotEmpty()) {
        node.copy(text = null, children = updatedChildren)
      } else {
        node.copy(children = updatedChildren)
      }
    }

  private fun buildHierarchy(nodes: List<TreeNode>): List<TreeNode> {
    if (nodes.isEmpty()) return emptyList()

    val roots = mutableListOf<TreeNode>()
    val stack = mutableListOf<Pair<Int, MutableList<TreeNode>>>()

    for (node in nodes) {
      while (stack.isNotEmpty() && stack.last().first >= node.level) {
        stack.removeLast()
      }

      if (stack.isEmpty()) {
        roots.add(node)
        stack.add(node.level to mutableListOf())
      } else {
        val parentChildren = stack.last().second
        parentChildren.add(node)
        stack.add(node.level to mutableListOf())
      }
    }

    return attachChildren(roots, nodes)
  }

  private fun attachChildren(
    roots: List<TreeNode>,
    allNodes: List<TreeNode>
  ): List<TreeNode> {
    if (allNodes.isEmpty()) return emptyList()

    val result = mutableListOf<TreeNode>()
    var i = 0

    while (i < allNodes.size) {
      val node = allNodes[i]
      val children = mutableListOf<TreeNode>()
      var j = i + 1

      while (j < allNodes.size && allNodes[j].level > node.level) {
        children.add(allNodes[j])
        j++
      }

      val builtChildren = if (children.isNotEmpty()) {
        attachChildren(children, children)
      } else {
        emptyList()
      }

      result.add(node.copy(children = builtChildren))
      i = j
    }

    return result
  }

  private fun assignNodeIds(nodes: List<TreeNode>): List<TreeNode> {
    var counter = 1

    fun assign(nodeList: List<TreeNode>): List<TreeNode> {
      return nodeList.map { node ->
        val id = "%04d".format(counter++)
        val children = assign(node.children)
        node.copy(nodeId = id, children = children)
      }
    }

    return assign(nodes)
  }
}
