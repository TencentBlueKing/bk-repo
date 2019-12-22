package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("token信息")
data class Token(
    @ApiModelProperty("tokenID")
    val id: String,
    @ApiModelProperty("创建时间")
    val createdAt: LocalDateTime,
    @ApiModelProperty("过期时间")
    val expiredAt: LocalDateTime
)
