package com.tencent.bkrepo.repository.pojo.metadata

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 元数据删除请求
 *
 * @author: carrypan
 * @date: 2019-10-14
 */
@ApiModel("元数据删除请求")
data class MetadataDeleteRequest(
    @ApiModelProperty("仓库id")
    val repositoryId: String,
    @ApiModelProperty("路径")
    val fullPath: String,
    @ApiModelProperty("待删除的元数据key列表")
    val keyList: Set<String>
)
