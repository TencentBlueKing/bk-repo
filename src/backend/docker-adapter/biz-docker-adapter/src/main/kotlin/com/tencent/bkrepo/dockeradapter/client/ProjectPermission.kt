package com.tencent.bkrepo.dockeradapter.client;

import com.fasterxml.jackson.annotation.JsonProperty

data class ProjectPermission(
    @JsonProperty("project_view")
    val projectView: PermissionData
)