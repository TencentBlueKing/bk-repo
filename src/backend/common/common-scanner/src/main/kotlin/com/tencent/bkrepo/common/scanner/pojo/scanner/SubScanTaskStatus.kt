package com.tencent.bkrepo.common.scanner.pojo.scanner

/**
 * 子扫描任务状态
 */
enum class SubScanTaskStatus {
    /**
     * 从未扫描过
     */
    NEVER_SCANNED,
    /**
     * 子任务已创建
     */
    CREATED,

    /**
     * 已被拉取
     */
    PULLED,
    /**
     * 子任务已入队
     */
    ENQUEUED,

    /**
     * 扫描执行中
     */
    EXECUTING,

    /**
     * 扫描超时
     */
    TIMEOUT,
    /**
     * 扫描失败
     */
    FAILED,
    /**
     * 扫描成功
     */
    SUCCESS
}
