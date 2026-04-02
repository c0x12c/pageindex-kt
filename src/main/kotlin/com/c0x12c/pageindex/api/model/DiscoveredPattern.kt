package com.c0x12c.pageindex.api.model

import com.fasterxml.jackson.annotation.JsonProperty

data class DiscoveredPattern(
  @JsonProperty("regex") val regex: String,
  @JsonProperty("level") val level: Int,
  @JsonProperty("description") val description: String? = null
)
