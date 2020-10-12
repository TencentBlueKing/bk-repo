package com.tencent.bkrepo.dockerapi.client;

import com.fasterxml.jackson.annotation.JsonProperty

data class ProjectPermission(
    @JsonProperty("project_view")
    val projectView: PermissionData
)