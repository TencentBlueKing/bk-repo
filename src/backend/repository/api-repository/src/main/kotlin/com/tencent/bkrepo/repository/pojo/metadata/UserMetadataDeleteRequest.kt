package com.tencent.bkrepo.repository.pojo.metadata

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 元数据删除请求
 */
@ApiModel("元数据删除请求")
data class UserMetadataDeleteRequest(
    @ApiModelProperty("待删除的元数据key列表", required = true)
    val keyList: Set<String>
)
