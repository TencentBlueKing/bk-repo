package com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit

import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.scanner.pojo.request.LoadResultArguments
import io.swagger.annotations.ApiModelProperty

class ScancodeToolkitResultArguments(
    @ApiModelProperty("需要的许可id列表")
    val licenseIds: List<String>? = emptyList(),
    @ApiModelProperty("需要的许可风险等级列表")
    val riskLevels: List<String>? = emptyList(),
    @ApiModelProperty("扫描结果类型")
    val reportType: String,
    @ApiModelProperty("分页参数")
    val pageLimit: PageLimit = PageLimit()
) : LoadResultArguments(ScancodeToolkitScanner.TYPE)
