package com.tencent.bkrepo.nuget.pojo.v3.metadata.index

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DependencyGroups(
    val dependencies: List<Dependency>? = null,
    val targetFramework: String? = null
)
