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
     * 扫描停止
     */
    STOP,

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
    SUCCESS;

    companion object {
        /**
         * 判断[status]是否是已结束的状态
         */
        fun finishedStatus(status: SubScanTaskStatus): Boolean {
            return status == TIMEOUT || status == FAILED || status == SUCCESS
        }

        /**
         * 判断[status]是否是已结束的状态
         */
        fun finishedStatus(status: String): Boolean {
            return finishedStatus(valueOf(status))
        }
    }
}
