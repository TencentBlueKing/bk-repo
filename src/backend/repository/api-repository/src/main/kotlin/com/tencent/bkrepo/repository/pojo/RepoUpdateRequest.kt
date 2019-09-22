package com.tencent.bkrepo.repository.pojo

import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 更新仓库请求
 *
 * @author: carrypan
 * @date: 2019-09-22
 */
@ApiModel("更新仓库请求")
data class RepoUpdateRequest(
        @ApiModelProperty("仓库名称")
        val name: String?,
        @ApiModelProperty("仓库类别")
        val category: RepositoryCategoryEnum?,
        @ApiModelProperty("是否公开")
        val public: Boolean?,
        @ApiModelProperty("简要描述")
        val description: String?,
        @ApiModelProperty("扩展信息")
        val extension: Any?
)