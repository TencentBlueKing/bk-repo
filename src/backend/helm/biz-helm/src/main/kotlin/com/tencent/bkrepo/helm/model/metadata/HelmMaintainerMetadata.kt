package com.tencent.bkrepo.helm.model.metadata

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class HelmMaintainerMetadata(
    val name: String?,
    val email: String?,
    val url: String?
)