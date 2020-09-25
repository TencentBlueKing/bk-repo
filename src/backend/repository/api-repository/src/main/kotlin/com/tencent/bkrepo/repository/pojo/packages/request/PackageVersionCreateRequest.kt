package com.tencent.bkrepo.repository.pojo.packages.request

import com.tencent.bkrepo.repository.pojo.packages.PackageType
import io.swagger.annotations.ApiModelProperty

data class PackageVersionCreateRequest(
    @ApiModelProperty("项目id")
    val projectId: String,
    @ApiModelProperty("仓库名称")
    val repoName: String,
    @ApiModelProperty("包名称")
    val packageName: String,
    @ApiModelProperty("包唯一标识符")
    val packageKey: String,
    @ApiModelProperty("包类型")
    val packageType: PackageType,
    @ApiModelProperty("包简要描述")
    val packageDescription: String? = null,
    @ApiModelProperty("版本名称")
    val versionName: String,
    @ApiModelProperty("版本大小")
    val size: Long,
    @ApiModelProperty("版本描述文件路径")
    var manifestPath: String? = null,
    @ApiModelProperty("版本内容文件路径")
    var contentPath: String? = null,
    @ApiModelProperty("版本构件阶段")
    val stageTag: List<String>? = null,
    @ApiModelProperty("版本元数据")
    val metadata: Map<String, Any>? = null,
    @ApiModelProperty("是否允许覆盖")
    val overwrite: Boolean = false,
    @ApiModelProperty("创建人")
    val createdBy: String
)