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

package com.tencent.bkrepo.analyst.pojo.response

import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "扫描方案信息")
data class ScanPlanInfo(
    @get:Schema(title = "方案id")
    val id: String,
    @get:Schema(title = "方案名")
    val name: String?,
    @get:Schema(title = "方案类型")
    val planType: String,
    @get:Schema(title = "扫描类型")
    val scanTypes: List<String>,
    @get:Schema(title = "projectId")
    val projectId: String,
    @get:Schema(title = "方案状态")
    val status: String,
    @get:Schema(title = "累计扫描制品数")
    val artifactCount: Long = 0,
    @get:Schema(title = "严重漏洞数")
    val critical: Long = 0,
    @get:Schema(title = "高危漏洞数")
    val high: Long = 0,
    @get:Schema(title = "中危漏洞数")
    val medium: Long = 0,
    @get:Schema(title = "低危漏洞数")
    val low: Long = 0,
    @get:Schema(title = "漏洞总数")
    val total: Long = 0,
    @get:Schema(title = "创建者")
    val createdBy: String,
    @get:Schema(title = "创建时间")
    val createdDate: String,
    @get:Schema(title = "修改者")
    val lastModifiedBy: String,
    @get:Schema(title = "修改时间")
    val lastModifiedDate: String,
    @get:Schema(title = "最后扫描时间")
    val lastScanDate: String?,
    @get:Schema(title = "是否只读")
    val readOnly: Boolean = false
)
