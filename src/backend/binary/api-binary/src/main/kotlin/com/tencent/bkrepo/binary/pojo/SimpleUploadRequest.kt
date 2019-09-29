package com.tencent.bkrepo.binary.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 简单上传请求
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@ApiModel("简单上传请求")
data class SimpleUploadRequest(
    @ApiModelProperty("仓库id", required = true)
    val repositoryId: String,
    @ApiModelProperty("路径", required = true)
    val path: String,
    @ApiModelProperty("文件名称", required = true)
    val name: String,
    @ApiModelProperty("sha256", required = true)
    val sha256: String,
    @ApiModelProperty("是否覆盖", required = false)
    val overwrite: Boolean = false,
    @ApiModelProperty("过期时间，单位天(0代表永久保存)，默认永久", required = false, example = "0")
    val expires: Long = 0,
    @ApiModelProperty("上传用户", required = true)
    val user: String

)