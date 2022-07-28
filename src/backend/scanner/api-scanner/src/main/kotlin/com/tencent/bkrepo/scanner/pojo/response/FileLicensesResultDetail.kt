package com.tencent.bkrepo.scanner.pojo.response

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("制品许可详细信息")
data class FileLicensesResultDetail(
    @ApiModelProperty("许可id")
    val licenseId: String,
    @ApiModelProperty("许可全称")
    val fullName: String,
    @ApiModelProperty("风险等级")
    val riskLevel: String?,
    @ApiModelProperty("合规性")
    val compliance: Boolean?,
    @ApiModelProperty("OSI认证")
    val isOsiApproved: Boolean?,
    @ApiModelProperty("是否 FSF 认证免费")
    val isFsfLibre: Boolean?,
    @ApiModelProperty("是否推荐使用")
    val recommended: Boolean?,
    @ApiModelProperty("依赖路径")
    val dependentPath: String,
    @ApiModelProperty("描述")
    val description: String
)
