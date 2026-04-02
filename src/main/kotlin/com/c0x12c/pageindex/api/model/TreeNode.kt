package com.c0x12c.pageindex.api.model

import com.fasterxml.jackson.annotation.JsonIgnore

data class TreeNode(
  val nodeId: String,
  val title: String,
  val summary: String? = null,
  val level: Int,
  val startPage: Int,
  val endPage: Int,
  val children: List<TreeNode> = emptyList(),
  val text: String? = null
) {
  /** Not serialized: breaks cycles (parent → children → parent). */
  @JsonIgnore
  var parent: TreeNode? = null
    private set

  companion object {
    fun wireParents(nodes: List<TreeNode>, parent: TreeNode? = null) {
      for (node in nodes) {
        node.parent = parent
        wireParents(node.children, node)
      }
    }
  }
}
