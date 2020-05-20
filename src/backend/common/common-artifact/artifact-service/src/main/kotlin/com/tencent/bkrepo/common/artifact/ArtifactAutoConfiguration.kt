package com.tencent.bkrepo.common.artifact

import com.tencent.bkrepo.common.artifact.auth.AuthConfiguration
import com.tencent.bkrepo.common.artifact.event.ArtifactEventListener
import com.tencent.bkrepo.common.artifact.exception.ExceptionConfiguration
import com.tencent.bkrepo.common.artifact.health.ArtifactHealhConfiguration
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetricsConfiguration
import com.tencent.bkrepo.common.artifact.permission.PermissionConfiguration
import com.tencent.bkrepo.common.artifact.resolve.ResolverConfiguration
import com.tencent.bkrepo.common.artifact.webhook.WebHookService
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource
import org.springframework.core.Ordered

/**
 *
 * @author: carrypan
 * @date: 2019/11/21
 */
@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication
@PropertySource("classpath:common-artifact.properties")
@Import(
    AuthConfiguration::class,
    PermissionConfiguration::class,
    ResolverConfiguration::class,
    ExceptionConfiguration::class,
    ArtifactMetricsConfiguration::class,
    ArtifactHealhConfiguration::class
)
class ArtifactAutoConfiguration {

    @Bean
    fun artifactEventListener() = ArtifactEventListener()

    @Bean
    fun webHookService() = WebHookService()
}
