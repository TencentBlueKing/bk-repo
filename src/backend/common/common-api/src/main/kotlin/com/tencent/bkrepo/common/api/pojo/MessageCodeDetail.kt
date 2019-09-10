package com.tencent.bkrepo.common.api.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("返回码详情")
data class MessageCodeDetail(
    @ApiModelProperty("主键ID", required = true)
    val id: String,
    @ApiModelProperty("信息码", required = true)
    val messageCode: Int,
    @ApiModelProperty("模块代码", required = true)
    val moduleCode: Int,
    @ApiModelProperty("中文简体描述信息", required = true)
    var messageDetailZhCn: String,
    @ApiModelProperty("中文繁体描述信息", required = false)
    var messageDetailZhTw: String?,
    @ApiModelProperty("英文描述信息", required = false)
    var messageDetailEn: String?
)
