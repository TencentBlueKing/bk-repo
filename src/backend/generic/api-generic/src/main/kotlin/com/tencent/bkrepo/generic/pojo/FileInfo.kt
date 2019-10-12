package com.tencent.bkrepo.generic.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 文件信息
 *
 * @author: carrypan
 * @date: 2019-09-28
 */
@ApiModel("文件信息")
data class FileInfo(
    @ApiModelProperty("文件大小")
    val size: Long,
    @ApiModelProperty("文件256")
    val sha256: String,
    @ApiModelProperty("分块大小")
    val blockList: List<BlockInfo>
)
