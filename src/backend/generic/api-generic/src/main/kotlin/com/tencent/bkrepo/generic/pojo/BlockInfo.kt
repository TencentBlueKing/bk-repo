package com.tencent.bkrepo.generic.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 分块信息
 */
@ApiModel("分块信息")
data class BlockInfo(
    @ApiModelProperty("分块大小")
    val size: Long,
    @ApiModelProperty("分块sha256")
    val sha256: String,
    @ApiModelProperty("分块序号")
    val sequence: Int
)
