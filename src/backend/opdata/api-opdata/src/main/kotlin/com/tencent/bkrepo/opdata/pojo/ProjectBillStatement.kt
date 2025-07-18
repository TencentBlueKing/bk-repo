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

package com.tencent.bkrepo.opdata.pojo

import com.alibaba.excel.annotation.ExcelProperty
import com.alibaba.excel.annotation.write.style.ColumnWidth
import java.time.LocalDateTime

data class ProjectBillStatement(
    @ColumnWidth(30)
    @ExcelProperty(value = ["项目ID"], order = 0)
    var projectId: String,
    @ColumnWidth(20)
    @ExcelProperty(value = ["事业群"], order = 1)
    var bgName: String?,
    @ColumnWidth(20)
    @ExcelProperty(value = ["是否启用"], order = 2)
    var enabled: Boolean?,
    @ColumnWidth(20)
    @ExcelProperty(value = ["项目用量(GB)"], order = 3)
    var capSize: Long,
    @ColumnWidth(20)
    @ExcelProperty(value = ["流水线仓库用量(GB)"], order = 4)
    var pipelineCapSize: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["自定义仓库用量(GB)"], order = 5)
    var customCapSize: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["费用"], order = 6)
    var totalCost: Double = 0.0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["计费开始时间"], order = 7)
    val startDate: LocalDateTime? = LocalDateTime.now(),
    @ColumnWidth(20)
    @ExcelProperty(value = ["计费结束时间"], order = 8)
    val endDate: LocalDateTime? = LocalDateTime.now(),
)
