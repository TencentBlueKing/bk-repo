package com.tencent.bkrepo.generic.pojo.upload

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
    @ApiModelProperty("分块大小，不提供则不校验", required = false, example = "0")
    val size: Long? = null,
    @ApiModelProperty("分块256，不提供则不校验", required = false)
    val sha256: String? = null
)
