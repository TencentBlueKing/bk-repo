package com.tencent.bkrepo.analyst.configuration

import com.tencent.bkrepo.common.stream.constant.BinderType
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 报告结果导出配置
 */
@ConfigurationProperties("scanner.report-export")
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
     * 黑名单中的项目不导出结果报告，仅在白名单为空时生效
     */
    var projectBlackList: Set<String> = emptySet(),
    /**
     * 非空时仅白名单中的项目会导出报告
     */
    var projectWhiteList: Set<String> = emptySet(),
    /**
     * 非空时仅白名单中的扫描器会导出报告
     */
    var scannerWhiteList: Set<String> = emptySet()
)
