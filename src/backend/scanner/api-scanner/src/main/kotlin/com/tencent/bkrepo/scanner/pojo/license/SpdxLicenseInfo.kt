package com.tencent.bkrepo.scanner.pojo.license

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("SPDX许可证信息")
data class SpdxLicenseInfo(
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: String,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: String,
    @ApiModelProperty("许可证名称")
    val name: String,
    @ApiModelProperty("许可证标识符")
    val licenseId: String,
    @ApiModelProperty("指向其他许可证副本的交叉引用 URL")
    val seeAlso: MutableList<String>,
    @ApiModelProperty("对许可证文件的 HTML 格式的引用")
    val reference: String,
    @ApiModelProperty("是否被弃用")
    val isDeprecatedLicenseId: Boolean,
    @ApiModelProperty("OSI 是否已批准许可证")
    val isOsiApproved: Boolean,
    @ApiModelProperty("是否 FSF 认证免费")
    val isFsfLibre: Boolean?,
    @ApiModelProperty("包含许可证详细信息的 JSON 文件的 URL")
    val detailsUrl: String,
    @ApiModelProperty("是否信任")
    val isTrust: Boolean,
    @ApiModelProperty("风险等级")
    val risk: String?
)
