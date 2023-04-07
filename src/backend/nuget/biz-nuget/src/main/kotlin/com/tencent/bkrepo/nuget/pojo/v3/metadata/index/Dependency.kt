package com.tencent.bkrepo.nuget.pojo.v3.metadata.index

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Dependency(
    @JsonProperty("id")
    val id: String,
    val range: String? = null,
    val registration: URI? = null
)
