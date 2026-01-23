package com.tencent.bkrepo.pypi.artifact

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pypi")
data class PypiProperties(
    var domain: String = "localhost",
    var enableRegexQuery: Boolean = true,
    /**
     * simple索引是否优先使用正则查询，仅enableRegexQuery=true时有效
     */
    var preferRegexQuery: Boolean = false,
)
