package com.tencent.bkrepo.nuget.pojo.v3.metadata.page

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationPageItem
import java.net.URI

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegistrationPage(
    @JsonProperty("@id")
    val id: URI,
    @JsonProperty("@type")
    val type: String? = null,
    val count: Int,
    val items: List<RegistrationPageItem>,
    val lower: String,
    val parent: URI,
    val upper: String
)
