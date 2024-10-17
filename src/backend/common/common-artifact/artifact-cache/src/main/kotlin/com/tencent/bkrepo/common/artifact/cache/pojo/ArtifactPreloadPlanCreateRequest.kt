package com.tencent.bkrepo.common.artifact.cache.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("预加载执行计划创建清理")
data class ArtifactPreloadPlanCreateRequest(
    @ApiModelProperty("所属项目ID")
    val projectId: String,
    @ApiModelProperty("所属仓库")
    val repoName: String,
    @ApiModelProperty("待加载制品的完整路径")
    val fullPath: String,
    @ApiModelProperty("预加载计划执行毫秒时间戳")
    val executeTime: Long,
)
