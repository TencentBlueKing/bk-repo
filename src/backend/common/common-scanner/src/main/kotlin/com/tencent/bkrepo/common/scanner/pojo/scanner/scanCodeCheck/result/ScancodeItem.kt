package com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("ScancodeItem许可信息")
data class ScancodeItem(
    @ApiModelProperty("许可简称")
    val licenseId: String,
    @ApiModelProperty("许可全称")
    val fullName: String = "",
    @ApiModelProperty("许可描述")
    val description: String = "",
    // 风险等级暂时没有
    @ApiModelProperty("风险等级")
    val riskLevel: String? = null,
    @ApiModelProperty("依赖路径")
    val dependentPath: String,
    @ApiModelProperty("合规性")
    val compliance: Boolean? = null,
    @ApiModelProperty("推荐使用")
    val recommended: Boolean? = null,
    @ApiModelProperty("未知的")
    val unknown: Boolean = true,
    @ApiModelProperty("OSI认证")
    val isOsiApproved: Boolean? = null,
    @ApiModelProperty("FSF认证")
    val isFsfLibre: Boolean? = null
) {
    companion object {
        const val TYPE = "SCANCODE_ITEM"
    }
}
