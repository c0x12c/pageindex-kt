package com.c0x12c.pageindex.core.indexer

import com.c0x12c.pageindex.api.model.TreeNode

object NodeSplitter {
  fun splitOversizedNodes(nodes: List<TreeNode>, maxTextLength: Int): List<TreeNode> {
    return nodes.map { node ->
      val processedChildren = splitOversizedNodes(node.children, maxTextLength)
      val text = node.text.orEmpty()

      if (text.length <= maxTextLength) {
        node.copy(children = processedChildren)
      } else {
        val parts = splitText(text, maxTextLength)
        val pageRange = node.endPage - node.startPage + 1
        val pagesPerPart = (pageRange.toDouble() / parts.size).coerceAtLeast(1.0)

        val splitChildren = parts.mapIndexed { idx, partText ->
          val partStart = node.startPage + (idx * pagesPerPart).toInt()
          val partEnd = if (idx == parts.size - 1) {
            node.endPage
          } else {
            (node.startPage + ((idx + 1) * pagesPerPart).toInt() - 1).coerceAtMost(node.endPage)
          }

          TreeNode(
            nodeId = "${node.nodeId}_part_$idx",
            title = "${node.title} (Part ${idx + 1})",
            level = node.level + 1,
            startPage = partStart,
            endPage = partEnd,
            text = partText
          )
        }

        node.copy(
          text = null,
          children = splitChildren + processedChildren
        )
      }
    }
  }

  private fun splitText(text: String, maxLength: Int): List<String> {
    val parts = mutableListOf<String>()
    var offset = 0
    while (offset < text.length) {
      val end = (offset + maxLength).coerceAtMost(text.length)
      if (end < text.length) {
        val breakPoint = findParagraphBreak(text, offset, end)
        parts.add(text.substring(offset, breakPoint))
        offset = breakPoint
      } else {
        parts.add(text.substring(offset, end))
        offset = end
      }
    }
    return parts
  }

  private fun findParagraphBreak(text: String, start: Int, end: Int): Int {
    val searchFrom = start + (end - start) / 2
    val lastBreak = text.lastIndexOf("\n\n", end - 1)
    if (lastBreak >= searchFrom) {
      return lastBreak + 2
    }
    val lastNewline = text.lastIndexOf('\n', end - 1)
    if (lastNewline >= searchFrom) {
      return lastNewline + 1
    }
    return end
  }
}
