/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.api.pojo

import io.swagger.v3.oas.annotations.media.Schema
import kotlin.math.ceil

@Schema(title = "分页数据包装模型")
data class Page<out T>(
    @get:Schema(title = "页码(从1页开始)")
    val pageNumber: Int,
    @get:Schema(title = "每页多少条")
    val pageSize: Int,
    @get:Schema(title = "总记录条数")
    val totalRecords: Long,
    @get:Schema(title = "总页数")
    val totalPages: Long,
    @get:Schema(title = "数据列表")
    val records: List<T>
) {
    constructor(pageNumber: Int, pageSize: Int, totalRecords: Long, records: List<T>) : this(
        pageNumber = pageNumber,
        pageSize = pageSize,
        totalRecords = totalRecords,
        totalPages = ceil(totalRecords * 1.0 / pageSize).toLong(),
        records = records
    )

    /**
     * 兼容处理
     */
    @Deprecated("Will be removed", replaceWith = ReplaceWith("totalRecords"))
    fun getCount(): Long = this.totalRecords

    @Deprecated("Will be removed", replaceWith = ReplaceWith("pageNumber"))
    fun getPage(): Int = this.pageNumber
}
