package com.tencent.bkrepo.binary.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 取消分块上传请求，服务器释放资源
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@ApiModel("取消分块上传请求")
data class UploadAbortRequest(
    @ApiModelProperty("分块上传事物id")
    val uploadId: String
)
