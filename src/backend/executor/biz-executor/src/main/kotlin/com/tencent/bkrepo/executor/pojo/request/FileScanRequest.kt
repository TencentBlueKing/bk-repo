package com.tencent.bkrepo.executor.pojo.request

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("文件扫描请求")
data class FileScanRequest(
    @ApiModelProperty("项目ID")
    val taskId: String?,
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("仓库名")
    val repoName: String,
    @ApiModelProperty("完全路径")
    var fullPath: String
)
