package com.tencent.bkrepo.fs.server.config.properties

import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    DriveProperties::class,
    IoaProperties::class,
    StreamProperties::class,
    DevXProperties::class,
    JwtAuthProperties::class,
)
class PropertiesConfiguration
