package com.tencent.bkrepo.common.checker.pojo

import com.fasterxml.jackson.annotation.JsonAlias

data class Credits(
    @JsonAlias("NPM")
    val npm: String,
    @JsonAlias("NVD")
    val nvd: String,
    @JsonAlias("OSSINDEX")
    val ossIndex: String,
    @JsonAlias("RETIREJS")
    val retireJs: String
)
