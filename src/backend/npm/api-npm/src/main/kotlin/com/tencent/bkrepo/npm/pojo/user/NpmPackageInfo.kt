package com.tencent.bkrepo.npm.pojo.user

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("包的基本信息")
data class NpmPackageInfo (
    @ApiModelProperty("包名称")
    val name: String,
    @ApiModelProperty("最新版本信息")
    val latest: NpmPackageLatestVersionInfo
)