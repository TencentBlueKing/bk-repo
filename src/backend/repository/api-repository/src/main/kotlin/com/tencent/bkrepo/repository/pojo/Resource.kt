package com.tencent.bkrepo.repository.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiParam
import java.time.LocalDateTime


/**
 * 仓库信息
 * @author: carrypan
 * @date: 2019-09-10
 */
@ApiModel("仓库信息")
data class Resource(
        @ApiParam("资源id")
        val id: String?,
        @ApiParam("是否为文件夹")
        val folder: Boolean,
        @ApiParam("路径")
        val path: String,
        @ApiParam("资源名称")
        val name: String,
        @ApiParam("完整路径")
        val fullPath: Boolean,
        @ApiParam("文件大小，单位byte")
        val size: Long,
        @ApiParam("文件sha256")
        val sha256: String,
        @ApiParam("逻辑删除标记")
        val deleted: LocalDateTime,
        @ApiParam("所属仓库id")
        val repositoryId: String
)

