package com.tencent.bkrepo.nuget.pojo.v3.metadata.index

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.nuget.constant.PACKAGE_DEPENDENCY_GROUP
import java.net.URI

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DependencyGroups(
    @JsonProperty("@id")
    val id: URI? = null,
    @JsonProperty("@type")
    val type: String? = PACKAGE_DEPENDENCY_GROUP,
    val dependencies: List<Dependency>? = null,
    var targetFramework: String? = null
)
