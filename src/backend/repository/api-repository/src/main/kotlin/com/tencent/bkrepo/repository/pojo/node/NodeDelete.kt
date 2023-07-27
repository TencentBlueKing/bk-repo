package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

data class NodeDelete(
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String,
    @ApiModelProperty("是否为文件夹")
    val folder: Boolean,
    @ApiModelProperty("完整路径")
    val fullPath: String,
    @ApiModelProperty("创建时间")
    val createdDate: LocalDateTime,
    @ApiModelProperty("最近使用时间")
    val recentlyUseDate: LocalDateTime?

)
