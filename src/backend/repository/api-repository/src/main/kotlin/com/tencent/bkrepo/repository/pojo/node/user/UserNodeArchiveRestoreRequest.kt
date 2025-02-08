package com.tencent.bkrepo.repository.pojo.node.user

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("归档节点恢复请求")
data class UserNodeArchiveRestoreRequest(
    @ApiModelProperty("项目id")
    val projectId: String,
    @ApiModelProperty("仓库名称")
    val repoName: String,
    @ApiModelProperty("路径")
    val path: String?,
    @ApiModelProperty("元数据")
    val metadata: Map<String, String> = emptyMap(),
    @ApiModelProperty("恢复限制个数")
    val limit: Int = 10000,
)
