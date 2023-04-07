package com.tencent.bkrepo.nuget.pojo.v3.metadata.index

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.nuget.constant.TYPE_PACKAGE
import java.net.URI

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegistrationPageItem(
    @JsonProperty("@id")
    val id: URI,
    @JsonProperty("@type")
    val type: String? = TYPE_PACKAGE,
    @JsonProperty("@sourceType")
    var sourceType: ArtifactChannel? = ArtifactChannel.LOCAL,
    val catalogEntry: RegistrationCatalogEntry,
    val packageContent: URI
)
