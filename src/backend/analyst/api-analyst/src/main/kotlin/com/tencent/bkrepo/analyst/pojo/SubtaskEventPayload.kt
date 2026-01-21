package com.tencent.bkrepo.analyst.pojo

import java.time.LocalDateTime

data class SubtaskEventPayload(
    /**
     * 子扫描任务id
     */
    val taskId: String,
    /**
     * 所属扫描任务
     */
    val parentTaskId: String,
    /**
     * 子任务状态
     */
    val status: String,
    /**
     * 扫描器
     */
    val scanner: String,
    /**
     * 触发方式
     */
    val triggerType: String,
    /**
     * 文件所属项目
     */
    val projectId: String,
    /**
     * 文件所属仓库
     */
    val repoName: String,
    /**
     * 仓库类型
     */
    val repoType: String,
    /**
     * 包名
     */
    val packageKey: String? = null,
    /**
     * 包版本
     */
    val version: String? = null,
    /**
     * 文件完整路径
     */
    val fullPath: String,
    /**
     * 文件sha256
     */
    val sha256: String,
    /**
     * 文件大小
     */
    val size: Long,
    /**
     * 包大小
     */
    val packageSize: Long,
    /**
     * 任务创建人
     */
    val createdBy: String,
    /**
     * 创建时间
     */
    val createdDateTime: LocalDateTime,
    /**
     * 开始时间
     */
    val startDateTime: LocalDateTime? = null,
    /**
     * 结束时间
     */
    val finishedDateTime: LocalDateTime? = null,
)
