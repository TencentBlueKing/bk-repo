package com.tencent.bkrepo.binary.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 完成分块上传请求，服务器释放资源
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@ApiModel("完成分块上传请求")
data class UploadCompleteRequest(
    @ApiModelProperty("分块上传事物id")
    val uploadId: String,
    @ApiModelProperty("分块数量")
    val blockCount: Int
)
