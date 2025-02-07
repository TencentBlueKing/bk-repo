package com.tencent.bkrepo.nuget.pojo.user

import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag


@Schema(title = "nuget版本详情页")
data class PackageVersionInfo(
    @get:Schema(title = "基础信息")
    val basic: BasicInfo,
    @get:Schema(title = "元数据信息")
    val metadata: List<MetadataModel>
)

@Tag(name = "基础信息")
data class BasicInfo(
    @get:Schema(title = "版本字段")
    val version: String,
    @get:Schema(title = "完整路径")
    val fullPath: String,
    @get:Schema(title = "文件大小，单位byte")
    val size: Long,
    @get:Schema(title = "文件sha256")
    val sha256: String,
    @get:Schema(title = "文件md5")
    val md5: String,
    @get:Schema(title = "晋级状态标签")
    val stageTag: List<String>,
    @get:Schema(title = "所属项目id")
    val projectId: String,
    @get:Schema(title = "所属仓库名称")
    val repoName: String,
    @get:Schema(title = "下载次数")
    val downloadCount: Long,
    @get:Schema(title = "创建者")
    val createdBy: String,
    @get:Schema(title = "创建时间")
    val createdDate: String,
    @get:Schema(title = "修改者")
    val lastModifiedBy: String,
    @get:Schema(title = "修改时间")
    val lastModifiedDate: String
)
