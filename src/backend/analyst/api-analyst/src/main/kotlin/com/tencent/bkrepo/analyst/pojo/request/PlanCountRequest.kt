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

package com.tencent.bkrepo.analyst.pojo.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDateTime

@Schema(title = "指定扫描方案扫描过的制品统计信息请求")
data class PlanCountRequest(
    @get:Schema(title = "扫描方案所属项目id", required = true)
    val projectId: String,
    @get:Schema(title = "扫描方案id", required = true)
    val id: String,
    @get:Schema(title = "制品扫描任务创建时间(开始)")
    val startTime: Instant? = null,
    @get:Schema(title = "制品扫描任务创建时间(开始)")
    var startDateTime: LocalDateTime? = null,
    @get:Schema(title = "制品扫描任务创建时间(截止)")
    val endTime: Instant? = null,
    @get:Schema(title = "制品扫描任务创建时间(截止)")
    var endDateTime: LocalDateTime? = null
)
