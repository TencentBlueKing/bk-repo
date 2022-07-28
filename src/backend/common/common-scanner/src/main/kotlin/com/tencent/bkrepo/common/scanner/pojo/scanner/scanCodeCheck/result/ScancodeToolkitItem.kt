package com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.annotations.ApiModel

@ApiModel("scancode扫描结果")
data class ScancodeToolItem(
    val headers: List<Any>,
    val files: List<ScancodeToolkitItem>
)

@ApiModel("scancode_toolkit扫描结果映射")
data class ScancodeToolkitItem(
    val path: String,
    val type: String,
    val licenses: List<SubLicenseItem>,
    @JsonProperty("license_expressions")
    val licenseExpressions: List<String>,
    @JsonProperty("percentage_of_license_text")
    val percentageOfLicenseText: Double,
    @JsonProperty("scan_errors")
    val scanErrors: List<String>
)
