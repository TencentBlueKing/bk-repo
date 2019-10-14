package com.tencent.bkrepo.metadata.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 创建/更新元数据请求
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
@ApiModel("创建或更新元数据请求")
data class MetadataUpsertRequest(
    @ApiModelProperty("节点id")
    val nodeId: String,
    @ApiModelProperty("创建者")
    val operateBy: String,
    @ApiModelProperty("元数据key-value数据")
    val dataMap: Map<String, Any>
)
