package com.tencent.bkrepo.npm.pojo

import com.fasterxml.jackson.annotation.JsonInclude
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.npm.pojo.module.des.ModuleDepsInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("npm页面返回包装模型")
data class PackageInfoResponse(
    @ApiModelProperty("包名称")
    val name: String,
    @ApiModelProperty("包描述信息")
    val description: String,
    @ApiModelProperty("包的readme信息")
    val readme: String,
    @ApiModelProperty("包的tag信息")
    val currentTags: List<TagsInfo>,
    @ApiModelProperty("包的版本信息")
    val versions: List<TagsInfo>,
    @ApiModelProperty("包的主要贡献者信息")
    val maintainers: List<MaintainerInfo>,
    @ApiModelProperty("包的下载量信息")
    val downloadCount: List<DownloadCount>,
    @ApiModelProperty("包的依赖信息")
    val dependencies: List<DependenciesInfo>,
    @ApiModelProperty("包的开发依赖信息")
    val devDependencies: List<DependenciesInfo>,
    @ApiModelProperty("包的被依赖信息")
    val dependents: Page<ModuleDepsInfo>
)

data class TagsInfo(
    @ApiModelProperty("tag")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val tags: String? = null,
    @ApiModelProperty("版本")
    val version: String,
    @ApiModelProperty("时间")
    val time: String
)

data class MaintainerInfo(
    @ApiModelProperty("贡献者名称")
    val name: String,
    @ApiModelProperty("邮箱")
    val email: String
)

data class DependenciesInfo(
    @ApiModelProperty("包的名称")
    val name: String,
    @ApiModelProperty("版本")
    val version: String
)

data class DownloadCount(
    @ApiModelProperty("时间段")
    val description: String,
    @ApiModelProperty("下载量")
    val count: Int
)
