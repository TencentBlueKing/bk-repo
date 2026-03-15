package com.tencent.bkrepo.fs.server.config.properties.drive

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("drive")
data class DriveProperties(
    var listCountLimit: Int = 100000,
    var nameMaxLength: Int = 256,
    var descriptionMaxLength: Int = 1024,
)
