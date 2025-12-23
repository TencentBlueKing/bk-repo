package com.tencent.bkrepo.auth.pojo

import com.fasterxml.jackson.annotation.JsonProperty

data class DeptInfo(
    @JsonProperty("id")
    val id: Long,
    @JsonProperty("name")
    val name: String,
)
