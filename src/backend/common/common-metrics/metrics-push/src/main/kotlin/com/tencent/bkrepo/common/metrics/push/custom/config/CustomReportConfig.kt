package com.tencent.bkrepo.common.metrics.push.custom.config

import com.tencent.bkrepo.common.api.constant.StringPool
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("management.custom")
class CustomReportConfig {
    /** 当前部署环境标识，非空时自动注入到所有上报指标的 label 和事件的 dimension（key: env）*/
    var env: String = StringPool.EMPTY
}
