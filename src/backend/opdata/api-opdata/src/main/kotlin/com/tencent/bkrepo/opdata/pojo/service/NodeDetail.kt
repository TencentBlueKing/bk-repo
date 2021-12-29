package com.tencent.bkrepo.opdata.pojo.service

import io.swagger.annotations.ApiModelProperty

/**
 * 服务节点详细信息
 */
data class NodeDetail(
    @ApiModelProperty("正在下载的数量", required = true)
    val downloadingCount:Long,
    @ApiModelProperty("正在上传的数量", required = true)
    val uploadingCount: Long
)
