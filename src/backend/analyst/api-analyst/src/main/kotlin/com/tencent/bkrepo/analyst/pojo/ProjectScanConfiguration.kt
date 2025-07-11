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

package com.tencent.bkrepo.analyst.pojo

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema


@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "项目扫描配置")
data class ProjectScanConfiguration(
    @get:Schema(title = "项目ID")
    val projectId: String,
    @get:Schema(title = "项目优先级，值越小优先级越低")
    val priority: Int? = null,
    @get:Schema(title = "项目限制的扫描任务数量")
    val scanTaskCountLimit: Int? = null,
    @get:Schema(title = "项目扫描子任务数量限制")
    val subScanTaskCountLimit: Int? = null,
    @get:Schema(title = "自动扫描配置")
    val autoScanConfiguration: Map<String, AutoScanConfiguration>? = null,
    @get:Schema(title = "子任务分发器")
    val dispatcherConfiguration: List<DispatcherConfiguration>? = null
)

@Schema(title = "分发器配置")
data class DispatcherConfiguration(
    @get:Schema(title = "使用的分发器")
    val dispatcher: String,
    @get:Schema(title = "扫描器")
    val scanner: String
)
