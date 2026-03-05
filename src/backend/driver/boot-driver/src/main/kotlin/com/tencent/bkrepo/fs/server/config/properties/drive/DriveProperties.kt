package com.tencent.bkrepo.fs.server.config.properties.drive

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("drive")
data class DriveProperties(
    var listCountLimit: Long = 100000L
)
