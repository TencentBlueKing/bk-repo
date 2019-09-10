package com.tencent.bkrepo.repository.pojo

import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiParam


/**
 * 仓库信息
 * @author: carrypan
 * @date: 2019-09-10
 */
@ApiModel("仓库信息")
data class Repository(
        @ApiParam("仓库id")
        val id: String?,
        @ApiParam("仓库名称")
        val name: String,
        @ApiParam("仓库类型")
        val type: String,
        @ApiParam("仓库类别")
        val category: RepositoryCategoryEnum,
        @ApiParam("是否公开")
        val public: Boolean,
        @ApiParam("简要描述")
        val description: String?,
        @ApiParam("扩展信息")
        val extension: Any?,
        @ApiParam("所属项目id")
        val projectId: String
)

