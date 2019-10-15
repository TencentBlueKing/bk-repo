package com.tencent.bkrepo.generic.pojo.operate

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 文件移动请求
 *
 * @author: carrypan
 * @date: 2019-10-13
 */
@ApiModel("文件移动请求")
data class FileMoveRequest(
    @ApiModelProperty("目标路径", required = true)
    val toPath: String
)
