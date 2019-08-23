package com.tencent.repository.common.client

import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource

@Configuration
@EnableFeignClients(basePackages = ["com.tencent.repository"])
@PropertySource("classpath:/common-client.yml")
class ClientAutoConfiguration