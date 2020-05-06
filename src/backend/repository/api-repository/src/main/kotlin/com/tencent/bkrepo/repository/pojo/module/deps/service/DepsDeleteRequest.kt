package com.tencent.bkrepo.repository.pojo.module.deps.service

import com.tencent.bkrepo.repository.pojo.ServiceRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("删除依赖关系请求")
data class DepsDeleteRequest(
    @ApiModelProperty("所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("资源名称", required = true)
    val name: String? = null,
    @ApiModelProperty("被依赖资源名称", required = true)
    val deps: String,
    @ApiModelProperty("操作用户", required = true)
    override val operator: String
) : ServiceRequest
