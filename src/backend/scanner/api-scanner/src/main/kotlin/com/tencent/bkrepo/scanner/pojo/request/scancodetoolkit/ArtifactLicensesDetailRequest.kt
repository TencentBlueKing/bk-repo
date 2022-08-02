package com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("获取许可扫描详情请求")
data class ArtifactLicensesDetailRequest(
    @ApiModelProperty("项目id")
    var projectId: String?,
    @ApiModelProperty("子任务id")
    var subScanTaskId: String?,
    @ApiModelProperty("许可id")
    val licenseId: String? = null,
    @ApiModelProperty("风险等级")
    val riskLevel: String? = null,
    @ApiModelProperty("页数")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @ApiModelProperty("每页数量")
    val pageSize: Int = DEFAULT_PAGE_SIZE
)
