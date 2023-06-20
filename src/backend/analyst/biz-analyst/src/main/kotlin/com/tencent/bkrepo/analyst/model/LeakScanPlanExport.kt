package com.tencent.bkrepo.analyst.model

import com.alibaba.excel.annotation.ExcelProperty

data class LeakScanPlanExport(
    @ExcelProperty("制品名称")
    val name: String,
    @ExcelProperty("制品版本/存储路径")
    val versionOrFullPath: String,
    @ExcelProperty("所属仓库")
    val repoName: String,
    @ExcelProperty("质量规则")
    val qualityRedLine: String,
    @ExcelProperty("危急漏洞数")
    val critical: Long = 0,
    @ExcelProperty("高危漏洞数")
    val high: Long = 0,
    @ExcelProperty("中危漏洞数")
    val medium: Long = 0,
    @ExcelProperty("低危漏洞数")
    val low: Long = 0,
    @ExcelProperty("扫描完成时间")
    val finishTime: String,
    @ExcelProperty("扫描时长(秒)")
    val duration: Long
)
