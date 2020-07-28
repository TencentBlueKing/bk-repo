package com.tencent.bkrepo.composer.pojo

import com.fasterxml.jackson.databind.annotation.JsonSerialize

@JsonSerialize
data class Dist(
    val type: String,
    val url: String,
    val reference: String,
    val shasum: String
)
