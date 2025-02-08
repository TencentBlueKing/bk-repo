package com.tencent.bkrepo.analyst.model

import com.alibaba.excel.annotation.ExcelProperty
import com.alibaba.excel.annotation.write.style.ColumnWidth

data class ScanPlanExport(
    @ColumnWidth(30)
    @ExcelProperty(value = ["制品名称"], order = 0)
    val name: String,
    @ColumnWidth(30)
    @ExcelProperty(value = ["制品版本/存储路径"], order = 1)
    val versionOrFullPath: String,
    @ColumnWidth(15)
    @ExcelProperty(value = ["所属仓库"], order = 2)
    val repoName: String,
    @ColumnWidth(15)
    @ExcelProperty(value = ["扫描状态"], order = 3)
    val status: String,
    @ColumnWidth(20)
    @ExcelProperty(value = ["扫描完成时间"], order = 4)
    val finishTime: String,
    @ColumnWidth(20)
    @ExcelProperty(value = ["扫描时长(秒)"], order = 5)
    val duration: Long? = null,
    @ColumnWidth(15)
    @ExcelProperty(value = ["严重漏洞数"], order = 6)
    val critical: Long? = null,
    @ColumnWidth(15)
    @ExcelProperty(value = ["高危漏洞数"], order = 7)
    val high: Long? = null,
    @ColumnWidth(15)
    @ExcelProperty(value = ["中危漏洞数"], order = 8)
    val medium: Long? = null,
    @ColumnWidth(15)
    @ExcelProperty(value = ["低危漏洞数"], order = 9)
    val low: Long? = null,
    @ColumnWidth(15)
    @ExcelProperty(value = ["许可总数"], order = 10)
    val total: Long? = null,
    @ColumnWidth(15)
    @ExcelProperty(value = ["废弃许可数"], order = 11)
    val unRecommend: Long? = null,
    @ColumnWidth(15)
    @ExcelProperty(value = ["未知许可数"], order = 12)
    val unknown: Long? = null,
    @ColumnWidth(20)
    @ExcelProperty(value = ["不合规许可数"], order = 13)
    val unCompliance: Long? = null,
)
