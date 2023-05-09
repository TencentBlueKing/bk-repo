package com.tencent.bkrepo.analyst.pojo.report

import java.time.LocalDateTime

/**
 * 报告导出
 */
data class Report(
    /**
     * 任务id
     */
    val taskId: String,
    /**
     * 项目id
     */
    val projectId: String,
    /**
     * 制品类型
     */
    val artifactType: String,
    /**
     * 制品名称
     */
    val artifactName: String,
    /**
     * 制品大小
     */
    val artifactSize: Long,
    /**
     * 使用的扫描器
     */
    val scanner: String,
    /**
     * 开始时间
     */
    val startDateTime: LocalDateTime,
    /**
     * 结束时间
     */
    val finishedDateTime: LocalDateTime,
    /**
     * 存在漏洞的组件列表
     */
    val components: List<Component>
)
