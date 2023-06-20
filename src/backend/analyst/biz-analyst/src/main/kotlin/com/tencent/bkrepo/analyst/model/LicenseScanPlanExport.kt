package com.tencent.bkrepo.analyst.model

import com.alibaba.excel.annotation.ExcelProperty

data class LicenseScanPlanExport(
    @ExcelProperty("制品名称")
    val name: String,
    @ExcelProperty("制品版本/存储路径")
    val versionOrFullPath: String,
    @ExcelProperty("所属仓库")
    val repoName: String,
    @ExcelProperty("质量规则")
    val qualityRedLine: String,
    @ExcelProperty("许可总数")
    val total: Long,
    @ExcelProperty("不推荐使用数")
    val unRecommend: Long,
    @ExcelProperty("未知许可数")
    val unknown: Long,
    @ExcelProperty("不合规数")
    val unCompliance: Long,
    @ExcelProperty("扫描完成时间")
    val finishTime: String,
    @ExcelProperty("扫描时长(秒)")
    val duration: Long
)
