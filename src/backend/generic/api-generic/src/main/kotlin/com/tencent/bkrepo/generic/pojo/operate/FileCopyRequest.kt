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
    @ApiModelProperty("源项目id", required = true)
    val srcProjectId: String,
    @ApiModelProperty("源仓库名称", required = true)
    val srcRepoName: String,
    @ApiModelProperty("源节点路径", required = true)
    val srcFullPath: String,
    @ApiModelProperty("目的项目id", required = false)
    val destProjectId: String? = null,
    @ApiModelProperty("目的仓库名称", required = false)
    val destRepoName: String? = null,
    @ApiModelProperty("目的路径", required = true)
    val destPath: String,
    @ApiModelProperty("同名文件是否覆盖", required = false)
    val overwrite: Boolean = false
)
