package com.tencent.bkrepo.repository.pojo.metadata

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
    @ApiModelProperty("仓库id")
    val repositoryId: String,
    @ApiModelProperty("路径")
    val fullPath: String,
    @ApiModelProperty("元数据key-value数据")
    val metadata: Map<String, String>
)
