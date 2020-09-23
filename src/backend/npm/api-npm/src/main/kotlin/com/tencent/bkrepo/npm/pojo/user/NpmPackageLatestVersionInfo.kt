package com.tencent.bkrepo.npm.pojo.user

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("包的最新版本的信息")
data class NpmPackageLatestVersionInfo (
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: String,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: String,
    @ApiModelProperty("资源名称")
    val name: String,
    @ApiModelProperty("文件大小，单位byte")
    val size: Long,
    @ApiModelProperty("最新版本")
    val version: String? = null,
    // @ApiModelProperty("文件sha256")
    // val sha256: String? = null,
    // @ApiModelProperty("文件md5")
    // val md5: String? = null,
    @ApiModelProperty("制品晋级阶段")
    val stageTag: String? = null,
    // @ApiModelProperty("元数据")
    // val metadata: Map<String, String> ? = null,
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String
)