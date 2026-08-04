package com.tencent.bkrepo.pypi.artifact

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pypi")
class PypiProperties {
    var domain: String = "localhost"
    var enableRegexQuery: Boolean = true
    /**
     * 是否启用 LOCAL PyPI `/simple/{package}/` HTML 文件缓存（默认关闭）
     */
    var enableSimpleIndexCache: Boolean = false
    /**
     * 单包 simple HTML 缓存 TTL。按缓存节点 lastModifiedDate 判断过期；
     * 过期后按 miss 处理并允许重建。小于等于 0 表示不过期。
     */
    var simpleIndexCacheTtl: Duration = Duration.ofMinutes(1)
}
