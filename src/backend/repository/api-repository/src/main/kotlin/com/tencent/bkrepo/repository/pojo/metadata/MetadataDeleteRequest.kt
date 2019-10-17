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
    @ApiModelProperty("项目id", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("节点完整路径", required = true)
    val fullPath: String,
    @ApiModelProperty("待删除的元数据key列表", required = true)
    val keyList: Set<String>,

    @ApiModelProperty("操作用户", required = true)
    val operator: String
)
