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


@Schema(title = "制品许可扫描结果预览")
class FileLicensesResultOverview(
    @get:Schema(title = "子扫描任务id")
    val subTaskId: String,
    @get:Schema(title = "制品名")
    val name: String,
    @get:Schema(title = "packageKey")
    val packageKey: String? = null,
    @get:Schema(title = "制品版本")
    val version: String? = null,
    @get:Schema(title = "制品路径")
    val fullPath: String? = null,
    @get:Schema(title = "仓库类型")
    val repoType: String,
    @get:Schema(title = "仓库名")
    val repoName: String,

    @get:Schema(title = "高风险许可数")
    val high: Long = 0,
    @get:Schema(title = "中风险许可数")
    val medium: Long = 0,
    @get:Schema(title = "低风险许可数")
    val low: Long = 0,
    @get:Schema(title = "无风险许可数")
    val nil: Long = 0,
    @get:Schema(title = "许可总数")
    val total: Long = 0,

    @get:Schema(title = "完成时间")
    val finishTime: String?,
    @get:Schema(title = "是否通过质量规则")
    val qualityRedLine: Boolean? = null,
    @get:Schema(title = "扫描时方案的质量规则")
    val scanQuality: Map<String, Any>? = null,
    @get:Schema(title = "扫描时长")
    val duration: Long,
    @get:Schema(title = "扫描状态")
    val scanStatus: String
)
