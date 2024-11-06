package com.tencent.bkrepo.nuget.pojo.v3.metadata.index

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.nuget.constant.PACKAGE_REGISTRATION
import java.net.URI

/**
 * reference: https://docs.microsoft.com/en-us/nuget/api/registration-base-url-resource#registration-index
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegistrationIndex(
    @JsonProperty("@id")
    val id: URI? = null,
    @JsonProperty("@type")
    val type: List<String>? = listOf(PACKAGE_REGISTRATION),
    // The number of registration pages in the index
    val count: Int,
    // The array of registration pages
    val items: List<RegistrationItem>
)
