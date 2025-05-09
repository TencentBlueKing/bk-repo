package com.tencent.bkrepo.fs.server.config.properties

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    IoaProperties::class,
    StreamProperties::class,
)
class PropertiesConfiguration
