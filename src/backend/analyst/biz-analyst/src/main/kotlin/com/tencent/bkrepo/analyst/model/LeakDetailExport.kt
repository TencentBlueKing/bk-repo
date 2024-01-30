package com.tencent.bkrepo.analyst.model

import com.alibaba.excel.annotation.ExcelProperty
import com.alibaba.excel.annotation.write.style.ColumnWidth

data class LeakDetailExport(
    @ColumnWidth(16)
    @ExcelProperty("漏洞ID")
    val vulId: String,
    @ColumnWidth(15)
    @ExcelProperty("漏洞等级")
    val severity: String,
    @ColumnWidth(30)
    @ExcelProperty("所属依赖")
    val pkgName: String,
    @ColumnWidth(30)
    @ExcelProperty("引入版本")
    val installedVersion: String,
    @ColumnWidth(45)
    @ExcelProperty("漏洞名")
    val vulnerabilityName: String,
    @ColumnWidth(90)
    @ExcelProperty("漏洞描述")
    val description: String? = null,
    @ColumnWidth(120)
    @ExcelProperty("相关信息")
    val reference: String? = null,
    @ColumnWidth(45)
    @ExcelProperty("修复建议")
    val officialSolution: String? = null
)
