package com.tencent.bkrepo.common.api.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 数据id值
 *
 * @author: carrypan
 * @date: 2019-09-22
 */
@ApiModel("数据id值")
data class IdValue(
    @ApiModelProperty("id值")
    val id: String
)
