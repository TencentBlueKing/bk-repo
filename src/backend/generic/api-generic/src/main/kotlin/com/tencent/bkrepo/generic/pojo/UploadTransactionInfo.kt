package com.tencent.bkrepo.generic.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 分块上传预检结果
 */
@ApiModel("分块上传事物信息")
data class UploadTransactionInfo(
    @ApiModelProperty("分块上传id")
    val uploadId: String,
    @ApiModelProperty("上传有效期")
    val expireSeconds: Long
)
