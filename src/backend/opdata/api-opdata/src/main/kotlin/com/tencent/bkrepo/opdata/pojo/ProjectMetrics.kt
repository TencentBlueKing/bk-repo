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

package com.tencent.bkrepo.opdata.pojo

import com.alibaba.excel.annotation.ExcelProperty
import com.alibaba.excel.annotation.write.style.ColumnWidth
import java.time.LocalDateTime

data class ProjectMetrics(
    @ColumnWidth(30)
    @ExcelProperty(value = ["项目ID"], order = 0)
    var projectId: String,
    @ColumnWidth(20)
    @ExcelProperty(value = ["事业群"], order = 1)
    var bgName: String?,
    @ColumnWidth(20)
    @ExcelProperty(value = ["部门"], order = 2)
    var deptName: String?,
    @ColumnWidth(20)
    @ExcelProperty(value = ["中心"], order = 3)
    var centerName: String?,
    @ColumnWidth(20)
    @ExcelProperty(value = ["运营产品ID"], order = 4)
    var productId: Int?,
    @ColumnWidth(20)
    @ExcelProperty(value = ["是否启用"], order = 5)
    var enabled: Boolean?,
    @ColumnWidth(20)
    @ExcelProperty(value = ["节点个数"], order = 6)
    var nodeNum: Long,
    @ColumnWidth(20)
    @ExcelProperty(value = ["项目用量(GB)"], order = 7)
    var capSize: Long,
    @ColumnWidth(20)
    @ExcelProperty(value = ["项目用量变化量-1天](GB)"], order = 8)
    var capSizeOfOneDayBefore: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["项目用量变化量-1周(GB)"], order = 9)
    var capSizeOfOneWeekBefore: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["项目用量变化量-1月(GB)"], order = 10)
    var capSizeOfOneMonthBefore: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["流水线仓库用量(GB)"], order = 11)
    var pipelineCapSize: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["流水线仓库用量变化量-1天(GB)"], order = 12)
    var pCapSizeOfOneDayBefore: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["流水线仓库用量变化量-1周(GB)"], order = 13)
    var pCapSizeOfOneWeekBefore: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["流水线仓库用量变化量-1月(GB)"], order = 14)
    var pCapSizeOfOneMonthBefore: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["自定义仓库用量(GB)"], order = 15)
    var customCapSize: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["自定义仓库用量变化量-1天(GB)"], order = 16)
    var cCapSizeOfOneDayBefore: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["自定义仓库用量变化量-1周(GB)"], order = 17)
    var cCapSizeOfOneWeekBefore: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["自定义仓库用量变化量-1月(GB)"], order = 18)
    var cCapSizeOfOneMonthBefore: Long = 0,
    @ColumnWidth(20)
    @ExcelProperty(value = ["统计时间"], order = 19)
    val createdDate: LocalDateTime? = LocalDateTime.now(),
)
