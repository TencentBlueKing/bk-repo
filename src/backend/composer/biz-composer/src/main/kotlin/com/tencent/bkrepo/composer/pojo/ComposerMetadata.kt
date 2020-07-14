package com.tencent.bkrepo.composer.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonSerialize

@JsonSerialize
data class ComposerMetadata(
    val name: String,
    val version: String,
    val description: String?,
    val keywords: JsonNode?,
    @JsonProperty("version_normalized")
    val versionNormalized: String?,
    val license: JsonNode?,
    val authors: JsonNode?,
    val source: JsonNode?,
    val dist: Dist?,
    val time: String?,
    val type: String,
    val require: Map<String, String>?,
    @JsonProperty("require-dev")
    val requireDev: Map<String, String>?,
    val other: Map<String, Any>?,
    val uid: Long?,
    val homepage: String?
)
