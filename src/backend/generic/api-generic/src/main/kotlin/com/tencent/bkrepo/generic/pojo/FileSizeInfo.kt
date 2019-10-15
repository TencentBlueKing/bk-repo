package com.tencent.bkrepo.generic.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 文件(夹)大小信息
 *
 * @author: carrypan
 * @date: 2019-10-15
 */
@ApiModel("文件(夹)大小信息")
data class FileSizeInfo(
    @ApiModelProperty("子文件(夹)数量")
    val subFileCount: Long,
    @ApiModelProperty("大小")
    val size: Long
)
