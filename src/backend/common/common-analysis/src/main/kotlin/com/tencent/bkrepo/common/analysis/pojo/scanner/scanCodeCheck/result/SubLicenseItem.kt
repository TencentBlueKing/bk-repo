/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.analysis.pojo.scanner.scanCodeCheck.result

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "scancode_toolkit扫描结果许可信息部分")
data class SubLicenseItem(
    val key: String,
    val score: Float,
    val name: String,
    @JsonProperty("short_name")
    val shortName: String,
    val category: String,
    @JsonProperty("is_exception")
    val exception: Boolean,
    @JsonProperty("is_unknown")
    val unknown: Boolean,
    val owner: String,
    @JsonProperty("homepage_url")
    val homepageUrl: String?,
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
