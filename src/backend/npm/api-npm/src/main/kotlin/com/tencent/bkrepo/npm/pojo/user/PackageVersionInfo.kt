package com.tencent.bkrepo.npm.pojo.user

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.npm.pojo.module.des.ModuleDepsInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("npm版本详情页返回包装模型")
data class PackageVersionInfo(
    @ApiModelProperty("基础信息")
    val basic: BasicInfo,
    @ApiModelProperty("元数据信息")
    val metadata: Map<String, String>,
    @ApiModelProperty("依赖信息")
    val dependencyInfo: VersionDependenciesInfo
)

@Api("基础信息")
data class BasicInfo(
    @ApiModelProperty("完整路径")
    val fullPath: String,
    @ApiModelProperty("文件大小，单位byte")
    val size: Long,
    @ApiModelProperty("文件sha256")
    val sha256: String,
    @ApiModelProperty("文件md5")
    val md5: String,
    @ApiModelProperty("晋级状态标签")
    val stageTag: String?,
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String,
    @ApiModelProperty("下载次数")
    val downloadCount: Int,
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: String,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: String
)

@Api("版本的依赖信息")
data class VersionDependenciesInfo(
    @ApiModelProperty("包的依赖信息")
    val dependencies: List<DependenciesInfo>,
    @ApiModelProperty("包的开发依赖信息")
    val devDependencies: List<DependenciesInfo>,
    @ApiModelProperty("包的被依赖信息")
    val dependents: Page<ModuleDepsInfo>
)