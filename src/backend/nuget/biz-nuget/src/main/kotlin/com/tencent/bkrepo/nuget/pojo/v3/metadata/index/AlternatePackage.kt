package com.tencent.bkrepo.nuget.pojo.v3.metadata.index

import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.nuget.constant.ID

data class AlternatePackage(
    @JsonProperty(ID)
    val packageId: String,
    val range: String? = null
)
