package com.tencent.bkrepo.analyst.configuration

import com.tencent.bkrepo.common.stream.constant.BinderType

/**
 * 报告结果导出配置
 */
data class ReportExportProperties(
    /**
     * 是否导出结果报告
     */
    var enabled: Boolean = false,
    /**
     * 导出到消息队列时的topic
     */
    var topic: String? = null,
    /**
     * 目标binder类型
     */
    var binderType: String? = BinderType.KAFKA.name,
    /**
     * 黑名单中的项目不导出结果报告
     */
    var projectsBlackList: List<String> = emptyList()
)
