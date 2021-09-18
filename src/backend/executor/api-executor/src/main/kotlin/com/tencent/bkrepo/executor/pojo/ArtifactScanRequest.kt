package com.tencent.bkrepo.executor.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("启动扫描任务请求")
data class ArtifactScanRequest(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("仓库名")
    val repoName: String,
    @ApiModelProperty("完全路径")
    var fullPath: String,
    @ApiModelProperty("sha256")
    val sha256: String
)
