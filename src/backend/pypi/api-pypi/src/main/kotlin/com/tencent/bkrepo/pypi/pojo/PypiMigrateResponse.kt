package com.tencent.bkrepo.pypi.pojo

import io.swagger.annotations.ApiModelProperty

data class PypiMigrateResponse<T>(
        @ApiModelProperty("描述")
        val description: String,
        @ApiModelProperty("总的包数量")
        val totalCount: Int = 0,
        @ApiModelProperty("迁移成功数量")
        val successCount: Int = 0,
        @ApiModelProperty("迁移失败数量")
        val failCount: Int = 0,
        @ApiModelProperty("耗时 单位s")
        val elapseTimeSeconds: Long = 0L,
        @ApiModelProperty("迁移失败数据")
        val failSet: Set<T?> = emptySet()
)