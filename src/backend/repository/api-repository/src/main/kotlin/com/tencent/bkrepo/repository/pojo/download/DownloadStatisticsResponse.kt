package com.tencent.bkrepo.repository.pojo.download

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("构建下载量信息")
data class DownloadStatisticsResponse(
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String,
    @ApiModelProperty("包名称")
    val packageName: String,
    @ApiModelProperty("包版本")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val version: String?,
    @ApiModelProperty("下载量")
    val count: Long
)
