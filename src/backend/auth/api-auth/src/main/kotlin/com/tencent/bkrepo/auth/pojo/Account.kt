package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("账号信息")
data class Account(
    @ApiModelProperty("appId")
    val appId: String,
    @ApiModelProperty("locked状态")
    val locked: Boolean,
    @ApiModelProperty("认证ak/sk对")
    val credentials: List<CredentialSet>
)
