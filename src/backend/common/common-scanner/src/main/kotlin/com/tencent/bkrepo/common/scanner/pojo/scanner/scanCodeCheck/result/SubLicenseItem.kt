package com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.annotations.ApiModel

@ApiModel("scancode_toolkit扫描结果许可信息部分")
data class SubLicenseItem(
    val key: String,
    val score: Float,
    val name: String,
    val short_name: String,
    val category: String,
    @JsonProperty("is_exception")
    val exception: Boolean,
    @JsonProperty("is_unknown")
    val unknown: Boolean,
    val owner: String,
    val homepage_url: String?,
    @JsonProperty("text_url")
    val textUrl: String,
    @JsonProperty("reference_url")
    val referenceUrl: String,
    @JsonProperty("scancode_text_url")
    val scancodeTextUrl: String,
    @JsonProperty("scancode_data_url")
    val scancodeDataUrl: String,
    @JsonProperty("spdx_license_key")
    val spdxLicenseKey: String,
    @JsonProperty("spdx_url")
    val spdxUrl: String,
    @JsonProperty("start_line")
    val startLine: Int,
    @JsonProperty("end_line")
    val endLine: Int,
    @JsonProperty("matched_rule")
    val matchedRule: Map<String, Any>
)
