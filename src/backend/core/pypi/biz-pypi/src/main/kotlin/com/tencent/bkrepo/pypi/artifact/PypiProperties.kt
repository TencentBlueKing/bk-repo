package com.tencent.bkrepo.pypi.artifact

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pypi")
class PypiProperties {
    var domain: String = "localhost"
    var enableRegexQuery: Boolean = true
    /**
     * 是否启用 LOCAL PyPI `/simple/{package}/` HTML 文件缓存（默认关闭）
     */
    var enableSimpleIndexCache: Boolean = false
}
