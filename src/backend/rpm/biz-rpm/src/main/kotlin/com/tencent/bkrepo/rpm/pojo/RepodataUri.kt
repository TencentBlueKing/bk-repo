package com.tencent.bkrepo.rpm.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("索引目录数据类")
data class RepodataUri(
    @ApiModelProperty("契合本次请求的repodata_depth 目录路径")
    val repodataPath: String,
    @ApiModelProperty("构件相对于索引文件的保存路径")
    val artifactRelativePath: String
)
