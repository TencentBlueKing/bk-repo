package com.tencent.bkrepo.fs.server.config.properties

import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    IoaProperties::class,
    StreamProperties::class,
    DevXProperties::class,
    JwtAuthProperties::class,
)
class PropertiesConfiguration
