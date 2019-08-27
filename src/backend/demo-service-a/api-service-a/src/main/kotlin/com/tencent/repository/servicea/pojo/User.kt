package com.tencent.repository.servicea.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("用户信息")
data class User(
    @ApiModelProperty("姓名", required = true)
    val name: String,
    @ApiModelProperty("年龄", required = true)
    val age: Int
)
