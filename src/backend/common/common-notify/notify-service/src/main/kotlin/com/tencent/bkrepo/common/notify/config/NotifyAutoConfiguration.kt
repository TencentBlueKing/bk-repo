package com.tencent.bkrepo.common.notify.config

import com.tencent.bkrepo.common.notify.api.NotifyService
import com.tencent.bkrepo.common.notify.service.DevopsNotify
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 通知配置
 */
@Configuration
@EnableConfigurationProperties(NotifyProperties::class)
class NotifyAutoConfiguration {

    @Bean
    fun notifyService(properties: NotifyProperties): NotifyService {
        val notifyService = DevopsNotify(properties.devopsServer)
        logger.info("Initializing NotifyService[${DevopsNotify::class.simpleName}], devopsServer: ${properties.devopsServer}")
        return notifyService
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NotifyAutoConfiguration::class.java)
    }
}
