package com.tencent.bkrepo.analyst.model

import com.alibaba.excel.annotation.ExcelProperty

data class LeakDetailExport(
    @ExcelProperty("漏洞ID")
    val vulId: String,
    @ExcelProperty("漏洞等级")
    val severity: String,
    @ExcelProperty("所属依赖")
    val pkgName: String,
    @ExcelProperty("引入版本")
    val installedVersion: String,
    @ExcelProperty("漏洞名")
    val vulnerabilityName: String,
    @ExcelProperty("漏洞描述")
    val description: String? = null,
    @ExcelProperty("修复建议")
    val officialSolution: String? = null,
    @ExcelProperty("相关信息")
    val reference: String? = null
)
