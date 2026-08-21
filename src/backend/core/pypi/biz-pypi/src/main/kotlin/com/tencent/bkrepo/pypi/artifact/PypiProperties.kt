package com.tencent.bkrepo.pypi.artifact

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pypi")
class PypiProperties {
    var domain: String = "localhost"
    var enableRegexQuery: Boolean = true
    /**
     * 是否启用 LOCAL PyPI `/simple/` 与 `/simple/{package}/` HTML 文件缓存（默认关闭）
     */
    var enableSimpleIndexCache: Boolean = false
    /**
     * 索引文件 TTL。到期后仍返回已有文件，并由单飞请求覆盖重建。
     * 小于等于 0 表示不按时间刷新，仅上传/删除 invalidate。
     */
    var simpleIndexCacheTtl: Duration = Duration.ofMinutes(1)
}
