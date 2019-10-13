package com.tencent.bkrepo.generic.pojo.upload

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 分块上传预检请求
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@ApiModel("分块上传预检请求")
data class UploadPreCheckRequest(
    @ApiModelProperty("sha256, 不提供则不检验", required = false)
    val sha256: String?,
    @ApiModelProperty("是否覆盖已有文件", required = false)
    val overwrite: Boolean = false,
    @ApiModelProperty("文件过期时间，单位天(0代表永久保存)，默认永久", required = false, example = "0")
    val expires: Long = 0,
    @ApiModelProperty("文件完整路径，提供则覆盖url路径名，便于调试", required = false)
    val fullPath: String?
)
