package com.tencent.bkrepo.dockerapi.client;

import com.fasterxml.jackson.annotation.JsonProperty

data class PermissionData(
    @JsonProperty("is_allowed")
    val allowed: Boolean
)