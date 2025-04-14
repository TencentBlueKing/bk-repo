package com.tencent.bkrepo.maven.pojo.request

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("根据gav搜索jar相关信息")
data class MavenJarSearchRequest(
    @ApiModelProperty("文件名")
    val fileList: List<String>,
)
