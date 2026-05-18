package com.tencent.bkrepo.replication.pojo.federation

/**
 * 联邦成员状态枚举
 *
 */
enum class FederationMemberStatus(val displayName: String, val description: String) {
    /**
     * 健康状态
     * 与成员正常通信，同步无延迟
     */
    HEALTHY("健康", "与成员正常通信，同步无延迟"),

    /**
     * 延迟状态
     * 与成员可通信，但同步存在延迟
     */
    DELAYED("延迟", "同步存在延迟"),

    /**
     * 错误状态
     * 同步过程中出现错误
     */
    ERROR("错误", "同步出现错误"),

    /**
     * 禁用状态
     * 成员已被禁用
     */
    DISABLED("禁用", "成员已被禁用"),

    /**
     * 不支持状态
     * 成员版本不支持联邦功能
     */
    UNSUPPORTED("不支持", "版本不支持联邦功能"),

    /**
     * 未知状态
     * 无法确定成员状态
     */
    UNKNOWN("未知", "无法确定成员状态");

    companion object {
        /**
         * 根据同步统计数据计算成员状态
         *
         * @param enabled 是否启用
         * @param connected 是否可连接
         * @param eventLag 延迟事件数
         * @param failureCount 错误数
         * @param fileLag 文件待传输数
         * @return 成员状态
         */
        fun calculateStatus(
            enabled: Boolean,
            connected: Boolean,
            eventLag: Long = 0,
            failureCount: Long = 0,
            fileLag: Long = 0,
        ): FederationMemberStatus {
            return when {
                !enabled -> DISABLED
                !connected -> ERROR
                failureCount > 0 -> ERROR
                eventLag > 0 || fileLag > 0 -> DELAYED  // 延迟超过1个事件或者还有1个以上文件没传输认为是延迟状态
                else -> HEALTHY
            }
        }
    }
}


