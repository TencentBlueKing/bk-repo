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
        private val toProjectId: String,
        @ApiModelProperty("目标仓库", required = false)
        private val toRepoName: String,
        @ApiModelProperty("目标路径", required = true)
        private val toPath: String
)