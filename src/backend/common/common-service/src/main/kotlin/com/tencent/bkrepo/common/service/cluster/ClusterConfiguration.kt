package com.tencent.bkrepo.common.service.cluster

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@EnableConfigurationProperties(ClusterProperties::class)
@Import(StandaloneJobAspect::class)
class ClusterConfiguration
