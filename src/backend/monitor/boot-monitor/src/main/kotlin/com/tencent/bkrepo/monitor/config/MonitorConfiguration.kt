package com.tencent.bkrepo.monitor.config

import com.tencent.bkrepo.monitor.export.InfluxExportProperties
import de.codecentric.boot.admin.server.config.EnableAdminServer
import de.codecentric.boot.admin.server.domain.values.InstanceId
import de.codecentric.boot.admin.server.services.InstanceIdGenerator
import de.codecentric.boot.admin.server.web.client.HttpHeadersProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.http.HttpHeaders
import org.springframework.util.Base64Utils
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.net.URL
import java.util.concurrent.Executor
import javax.annotation.Resource

@Configuration
@EnableAdminServer
@EnableConfigurationProperties(MonitorProperties::class, InfluxExportProperties::class)
@ConfigurationProperties("spring.boot.admin.auth")
class MonitorConfiguration {

    @Resource
    private lateinit var taskAsyncExecutor: Executor

    var username: String? = null
    var password: String? = null

    @Bean
    @ConditionalOnProperty("spring.boot.admin.auth.username")
    fun customHttpHeadersProvider(): HttpHeadersProvider {
        return HttpHeadersProvider {
            val httpHeaders = HttpHeaders()
            httpHeaders.add(HttpHeaders.AUTHORIZATION, "Basic " + Base64Utils.encodeToString("$username:$password".toByteArray()))
            httpHeaders
        }
    }

    @Bean
    fun monitorWebMvcConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun configureAsyncSupport(configurer: AsyncSupportConfigurer) {
                super.configureAsyncSupport(configurer)
                configurer.setDefaultTimeout(60 * 1000L)
                configurer.setTaskExecutor(taskAsyncExecutor as AsyncTaskExecutor)
            }
        }
    }

    @Bean
    fun instanceIdGenerator(): InstanceIdGenerator = InstanceIdGenerator {
        val url = URL(it.serviceUrl)
        InstanceId.of("${url.host}-${url.port}")
    }
}
