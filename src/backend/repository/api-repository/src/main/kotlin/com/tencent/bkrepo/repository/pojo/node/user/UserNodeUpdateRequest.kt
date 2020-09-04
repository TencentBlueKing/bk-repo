package com.tencent.bkrepo.repository.pojo.node.user

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("节点更新请求")
data class UserNodeUpdateRequest(
    @ApiModelProperty("过期时间，单位天(0代表永久保存)")
    val expires: Long = 0
)
