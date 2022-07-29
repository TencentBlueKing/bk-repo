package com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result

import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("scancode_toolkit扫描器扫描结果")
data class ScanCodeToolkitScanExecutorResult(
    override val scanStatus: String,
    override val overview: Map<String, Any?>,
    @ApiModelProperty("结果")
    val scancodeItem: List<ScancodeItem>
) : ScanExecutorResult(scanStatus, overview, ScancodeToolkitScanner.TYPE) {
    companion object {
        fun overviewKeyOf(level: String): String {
            return "license${level.capitalize()}Count"
        }

        fun overviewKeyOfLicenseRisk(riskLevel: String?): String {
            val level = if (riskLevel.isNullOrEmpty()) {
                // 扫描器尚未支持的证书类型数量KEY
                "notAvailable"
            } else {
                riskLevel
            }
            return "licenseRisk${level.capitalize()}Count"
        }
    }
}

