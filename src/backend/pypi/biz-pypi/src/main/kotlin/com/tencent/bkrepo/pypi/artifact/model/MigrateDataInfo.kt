package com.tencent.bkrepo.pypi.artifact.model

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("迁移数据结果")
data class MigrateDataInfo(
        @ApiModelProperty("创建者")
        val createdBy: String,
        @ApiModelProperty("创建时间")
        val createdDate: String,
        @ApiModelProperty("迁移包数量")
        val packagesNum: Int,
        @ApiModelProperty("总的文件数量")
        val filesNum: Int,
        @ApiModelProperty("执行时间")
        val elapseTimeSeconds: Long,
        @ApiModelProperty("错误数据")
        val errorData: Set<Any?>,
        @ApiModelProperty("所属项目id")
        val projectId: String,
        @ApiModelProperty("所属仓库名称")
        val repoName: String,
        @ApiModelProperty("迁移结果描述")
        val description: String
)