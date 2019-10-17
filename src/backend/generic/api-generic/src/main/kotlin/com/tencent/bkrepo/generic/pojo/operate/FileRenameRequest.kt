package com.tencent.bkrepo.generic.pojo.operate

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 文件重命名请求
 *
 * @author: carrypan
 * @date: 2019-10-17
 */
@ApiModel("文件重命名请求")
data class FileRenameRequest(
    @ApiModelProperty("新的完整路径", required = true)
    val newFullPath: String
)
