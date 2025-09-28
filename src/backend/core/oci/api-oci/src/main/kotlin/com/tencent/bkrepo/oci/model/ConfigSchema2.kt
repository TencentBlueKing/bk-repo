package com.tencent.bkrepo.oci.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ConfigSchema2(
    var architecture: String,
    var history: List<History>?,
    var os: String,
    val variant: String? = null
)

data class History(
    var created: String,
    @JsonProperty("created_by")
    var createdBy: String? = null
)
