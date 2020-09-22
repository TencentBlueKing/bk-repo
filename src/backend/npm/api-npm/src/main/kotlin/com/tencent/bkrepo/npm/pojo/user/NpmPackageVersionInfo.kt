package com.tencent.bkrepo.npm.pojo.user

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("包的版本信息")
data class NpmPackageVersionInfo (
    @ApiModelProperty("资源名称")
    val name: String,
    @ApiModelProperty("文件大小，单位byte")
    val size: Long,
    @ApiModelProperty("当前版本")
    val version: String,
    @ApiModelProperty("制品晋级阶段")
    val stageTag: String? = null,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: String,
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String
)