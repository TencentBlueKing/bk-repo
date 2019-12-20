package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.CredentialStatus
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("账户认证信息")
data class CredentialSet(
    @ApiModelProperty("accessKey")
    val accessKey: String,
    @ApiModelProperty("secretKey")
    val secretKey: String,
    @ApiModelProperty("创建时间")
    val createdAt: LocalDateTime,
    @ApiModelProperty("状态")
    val status: CredentialStatus
)