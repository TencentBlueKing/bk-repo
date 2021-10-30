package com.tencent.bkrepo.executor.pojo.request

import com.tencent.bkrepo.common.query.enums.OperationType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("文件扫描请求")
data class RepoScanRequest(
    @ApiModelProperty("项目ID")
    val taskId: String?,
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("仓库名")
    val repoName: String,
    @ApiModelProperty("匹配规则")
    var name: String?,
    @ApiModelProperty("匹配规则")
    var rule: OperationType?
)
