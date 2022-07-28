package com.tencent.bkrepo.scanner.pojo.response

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("许可扫描方案信息")
class ScanLicensePlanInfo(
    @ApiModelProperty("方案id")
    val id: String,
    @ApiModelProperty("方案名")
    val name: String?,
    @ApiModelProperty("方案类型")
    val planType: String,
    @ApiModelProperty("projectId")
    val projectId: String,
    @ApiModelProperty("方案状态")
    val status: String,
    @ApiModelProperty("累计扫描制品数")
    val artifactCount: Long = 0,
    @ApiModelProperty("许可证总数")
    val total: Long = 0,
    @ApiModelProperty("不推荐使用的许可数")
    val unRecommend: Long = 0,
    @ApiModelProperty("未知的许可数")
    val unknown: Long = 0,
    @ApiModelProperty("不合规的许可数")
    val unCompliance: Long = 0,
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: String,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: String,
    @ApiModelProperty("最后扫描时间")
    val lastScanDate: String?
)
