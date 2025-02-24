package com.tencent.bkrepo.opdata.pojo.config

import io.swagger.v3.oas.annotations.media.Schema


data class GetConfigRequest(
    @get:Schema(title = "查询的应用名")
    val appName: String = "",
    @get:Schema(title = "查询的profile")
    val profile: String = "",
    @get:Schema(title = "查询的key")
    val key:String
)
