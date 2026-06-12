package com.tencent.bkrepo.repository.pojo.ilnet

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

data class LinkTrafficRequest(
    @get:Schema(title = "终端 MAC 地址", required = true)
    val mac: String,
    @get:Schema(title = "终端 IP，仅用于响应回显")
    val ip: String? = "",
    @get:Schema(title = "用户名，仅用于响应回显")
    val username: String? = "",
    @get:Schema(title = "终端网速，仅用于响应回显")
    @JsonProperty("link_speed")
    val linkSpeed: String? = "",
    @get:Schema(title = "是否仅返回链路状态，默认 true")
    val simple: Boolean? = true,
    @get:Schema(title = "拥塞判定阈值（%），利用率 ≥ 该值视为拥塞，取值 10~100")
    @JsonProperty("congestion_threshold")
    val congestionThreshold: Float? = 80.0f,
)
