package com.tencent.bkrepo.generic.pojo

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
    @ApiModelProperty("分块sha256列表字符串, 不提供则不校验", required = false, example = "1,2,3")
    val blockSha256ListStr: String? = null
)
