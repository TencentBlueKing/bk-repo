package com.tencent.bkrepo.auth.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.mongodb.core.mapping.Field

data class DeptInfo(
    @Field("id")
    @JsonProperty("id")
    val id: Long,
    @JsonProperty("name")
    val name: String,
)
