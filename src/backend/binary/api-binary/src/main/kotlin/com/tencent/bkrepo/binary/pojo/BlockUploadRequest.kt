package com.tencent.bkrepo.binary.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 分块上传请求
 *
 * @author: carrypan
 * @date: 2019-09-28
 */
@ApiModel("分块上传请求")
data class BlockUploadRequest(
    @ApiModelProperty("分块上传事物id")
    val uploadId: String,
    @ApiModelProperty("是否覆盖", required = false)
    val sequence: Boolean = false,
    @ApiModelProperty("分块大小", required = true, example = "0")
    val size: Long,
    @ApiModelProperty("分块256", required = true)
    val sha256: String
)
