package com.tencent.bkrepo.binary.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile

/**
 * 分块上传预检结果
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@ApiModel("分块上传预检结果")
data class UploadPrecheckResult(
    @ApiModelProperty("分块上传事物id")
    val uploadId: String

)