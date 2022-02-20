package com.tencent.bkrepo.scanner.pojo

/**
 * 子扫描任务状态
 */
enum class SubScanTaskStatus {
    /**
     * 子任务已创建
     */
    CREATED,

    /**
     * 子任务已入队
     */
    ENQUEUED,

    /**
     * 扫描执行中
     */
    EXECUTING,

    /**
     * 执行结束
     */
    FINISHED
}
