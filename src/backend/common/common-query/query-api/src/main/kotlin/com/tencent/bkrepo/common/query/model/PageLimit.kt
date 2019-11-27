package com.tencent.bkrepo.common.query.model

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 分页限制
 *
 * @author: carrypan
 * @date: 2019/11/14
 */
@ApiModel("自定义查询模型")
data class PageLimit(
    @ApiModelProperty("当前页(第0页开始)")
    val current: Int = 0,
    @ApiModelProperty("每页数量")
    val size: Int = 20
)
