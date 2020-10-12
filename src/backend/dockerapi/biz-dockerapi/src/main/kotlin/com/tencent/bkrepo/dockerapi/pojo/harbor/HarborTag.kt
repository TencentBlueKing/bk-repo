package com.tencent.bkrepo.dockerapi.pojo.harbor

import com.fasterxml.jackson.annotation.JsonProperty

data class HarborTag(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("size")
    val size: Long,
    @JsonProperty("architecture")
    val architecture: String,
    @JsonProperty("os")
    val os: String,
    @JsonProperty("created")
    val created: String
)