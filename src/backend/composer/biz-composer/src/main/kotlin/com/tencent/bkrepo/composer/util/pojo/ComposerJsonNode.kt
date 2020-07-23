package com.tencent.bkrepo.composer.util.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("composer 'package.json'文件中节点")
data class ComposerJsonNode(
    @ApiModelProperty("composer package name")
    val packageName: String,
    @ApiModelProperty("composer package version")
    val version: String,
    @ApiModelProperty("composer package content")
    // 保存到 package.json 索引中的内容
    val json: String
)
