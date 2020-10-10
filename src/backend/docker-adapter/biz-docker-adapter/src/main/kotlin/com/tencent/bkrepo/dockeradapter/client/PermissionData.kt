package com.tencent.bkrepo.dockeradapter.client;

import com.fasterxml.jackson.annotation.JsonProperty

data class PermissionData(
    @JsonProperty("is_allowed")
    val allowed: Boolean
)