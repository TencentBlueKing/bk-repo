package com.tencent.repository.common.service

import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.Ordered

@Configuration
@PropertySource("classpath:/common-service.yml")
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@EnableDiscoveryClient
class ServiceAutoConfiguration