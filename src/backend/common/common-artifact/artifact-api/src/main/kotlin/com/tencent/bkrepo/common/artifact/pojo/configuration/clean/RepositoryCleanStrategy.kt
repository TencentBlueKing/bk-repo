package com.tencent.bkrepo.common.artifact.pojo.configuration.clean

import com.tencent.bkrepo.common.query.model.Rule
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("清理配置")
data class RepositoryCleanStrategy(
    @ApiModelProperty("清理任务状态", required = true)
    var status: CleanStatus = CleanStatus.WAITING,

    @ApiModelProperty("是否开启自动清理", required = true)
    val autoClean: Boolean = false,

    @ApiModelProperty("保留版本数", required = false)
    val reserveVersions: Long = 20,

    @ApiModelProperty("保留天数", required = true)
    val reserveDays: Long = 30,

    @ApiModelProperty("元数据保留规则", required = false)
    val rule: Rule?
)
