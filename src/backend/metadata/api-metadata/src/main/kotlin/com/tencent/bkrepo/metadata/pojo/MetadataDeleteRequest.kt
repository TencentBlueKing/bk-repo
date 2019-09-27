package com.tencent.bkrepo.metadata.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 删除元数据请求
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
@ApiModel("删除元数据请求")
data class MetadataDeleteRequest(
    @ApiModelProperty("节点id")
    val nodeId: String,
    @ApiModelProperty("待删除的元数据id列表")
    val metadataIdList: List<String>?,
    @ApiModelProperty("是否删除节点所有元数据。为true时将忽略metadataIdList参数")
    val deleteAll: Boolean = false
)