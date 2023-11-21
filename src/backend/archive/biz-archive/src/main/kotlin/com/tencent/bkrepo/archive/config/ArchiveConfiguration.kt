package com.tencent.bkrepo.archive.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@EnableConfigurationProperties(ArchiveProperties::class)
@Configuration
class ArchiveConfiguration
