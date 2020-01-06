package com.tencent.bkrepo.generic.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 文件详情
 *
 * @author: carrypan
 * @date: 2019-10-13
 */
@ApiModel("文件详情")
data class FileDetail(
    @ApiModelProperty("文件信息")
    val fileInfo: FileInfo,
    @ApiModelProperty("元数据列表")
    val metadata: Map<String, String>
)
