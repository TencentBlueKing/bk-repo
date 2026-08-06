package com.tencent.bkrepo.repository.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MetricsPushProperties::class)
class MetricsPushConfig
