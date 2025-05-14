package com.tencent.bkrepo.nuget.pojo.v3.metadata.index

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.nuget.constant.ID
import com.tencent.bkrepo.nuget.constant.PACKAGE_DEPENDENCY
import java.net.URI

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Dependency(
    @JsonProperty("@id")
    val id: URI? = null,
    @JsonProperty("@type")
    val type: String? = PACKAGE_DEPENDENCY,
    @JsonProperty(ID)
    val packageId: String,
    val range: String? = null,
    val registration: URI? = null
)
