/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.opdata.pojo

import io.swagger.v3.oas.annotations.media.Schema


data class QueryRequest(
//    @get:Schema(title = "requestId")
//    val requestId: String,
//    @get:Schema(title = "timezone")
//    val timezone: String,
//    @get:Schema(title = "panelId")
//    val panelId: Int,
//    @get:Schema(title = "dashboardId")
//    val dashboardId: Int,
//    @get:Schema(title = "range")
//    val range: Range,
//    @get:Schema(title = "interval")
//    val interval: String,
//    @get:Schema(title = "intervalMs")
//    val intervalMs: Long,
//    @get:Schema(title = "maxDataPoints")
//    val maxDataPoints: Long,
    @get:Schema(title = "targets")
    val targets: List<Target>
//    @get:Schema(title = "scopedVars")
//    val scopedVars: List<Target>,
//    @get:Schema(title = "startTime")
//    val startTime: Long,
//    @get:Schema(title = "rangeRaw")
//    val rangeRaw: Raw,
//    @get:Schema(title = "adhocFilters")
//    val adhocFilters: List<Filter>
)
