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

package com.tencent.bkrepo.analyst.pojo.request.standard

import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.response.filter.MergedFilterRule
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.query.model.PageLimit
import io.swagger.v3.oas.annotations.media.Schema


@Suppress("LongParameterList")
data class StandardLoadResultArguments(
    @get:Schema(title = "需要的cve列表")
    val vulIds: List<String> = emptyList(),
    @get:Schema(title = "需要的漏洞严重性等级列表")
    val vulnerabilityLevels: List<String> = emptyList(),
    @get:Schema(title = "需要的许可id列表")
    val licenseIds: List<String> = emptyList(),
    @get:Schema(title = "需要的许可id列表")
    val sensitiveContent: String? = null,
    @get:Schema(title = "需要的许可id列表")
    val sensitiveType: String? = null,
    @get:Schema(title = "扫描结果类型")
    val reportType: String,
    @get:Schema(title = "分页参数")
    val pageLimit: PageLimit = PageLimit(),
    @get:Schema(title = "漏洞过滤规则")
    val rule: MergedFilterRule? = null,
    @get:Schema(title = "是否仅包含被忽略的结果")
    val ignored: Boolean = false
) : LoadResultArguments(StandardScanner.TYPE)
