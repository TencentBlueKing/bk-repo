/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.analysis.pojo.scanner


import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ArrowheadScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.result.DependencyScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.scanner.DependencyScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.scanCodeCheck.result.ScanCodeToolkitScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanner
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "扫描结果")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ArrowheadScanExecutorResult::class, name = ArrowheadScanner.TYPE),
    JsonSubTypes.Type(value = DependencyScanExecutorResult::class, name = DependencyScanner.TYPE),
    JsonSubTypes.Type(value = TrivyScanExecutorResult::class, name = TrivyScanner.TYPE),
    JsonSubTypes.Type(value = ScanCodeToolkitScanExecutorResult::class, name = ScancodeToolkitScanner.TYPE),
    JsonSubTypes.Type(value = StandardScanExecutorResult::class, name = StandardScanner.TYPE)
)
open class ScanExecutorResult(
    @get:Schema(title = "扫描执行状态")
    open val scanStatus: String,
    @get:Schema(title = "扫描器类型")
    val type: String
) {
    /**
     * 标准化扫描结果
     */
    open fun normalizeResult() {}
}
