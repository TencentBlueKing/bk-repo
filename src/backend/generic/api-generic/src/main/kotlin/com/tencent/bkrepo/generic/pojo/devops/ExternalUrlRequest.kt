package com.tencent.bkrepo.generic.pojo.devops

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 创建外部链接请求
 */
@ApiModel("创建外部链接请求")
data class ExternalUrlRequest(
    @ApiModelProperty("项目id", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("目的项目id", required = true)
    val path: String,
    @ApiModelProperty("下载用户", required = true)
    val downloadUser: String,
    @ApiModelProperty("仓库名称", required = true)
    val ttl: Int,
    @ApiModelProperty("directed", required = false)
    val directed: Boolean = false
)