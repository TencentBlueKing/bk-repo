package com.tencent.bkrepo.fs.server.config.properties.drive

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("drive")
class DriveProperties {
    var listCountLimit: Int = 100000
    var nameMaxLength: Int = 256
    var descriptionMaxLength: Int = 1024

    /**
     * 同一仓库下两次快照创建的最小间隔时间，默认10秒。
     * 设置为 Duration.ZERO 表示不限制。
     */
    var snapshotCreateInterval: Duration = Duration.ofSeconds(10)

    /**
     * Drive block write并发请求数限制，<= 0 表示不限制。
     *
     * 在 reactive 场景下，连接总数不可控；当配置了 ReceiveProperties.fileSizeThreshold 后，
     * 每个连接都可能占用较大的接收缓存，连接数过多时可能触发 OOM，因此需要限制写请求并发数。
     */
    var writeRequestLimit: Int = 0
}