package com.tencent.bkrepo.generic.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 分块上传预检结果
 *
 * @author: carrypan
 * @date: 2019-09-30
 */
@ApiModel("分块上传事物信息")
data class UploadTransactionInfo(
    @ApiModelProperty("分块上传id")
    val uploadId: String,
    @ApiModelProperty("上传有效期, 单位s")
    val expires: Long
)
