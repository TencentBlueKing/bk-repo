package com.tencent.bkrepo.generic.pojo.operate

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 文件复制请求
 *
 * @author: carrypan
 * @date: 2019-10-13
 */
@ApiModel("文件复制请求")
data class FileCopyRequest(
    @ApiModelProperty("目标项目", required = false)
    val toProjectId: String,
    @ApiModelProperty("目标仓库", required = false)
    val toRepoName: String,
    @ApiModelProperty("目标路径", required = true)
    val toPath: String,
    @ApiModelProperty("是否覆盖同名文件", required = false)
    val overwrite: Boolean = false
)
