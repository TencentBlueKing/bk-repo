package com.tencent.bkrepo.common.artifact

import com.tencent.bkrepo.common.artifact.event.ArtifactEventListener
import com.tencent.bkrepo.common.artifact.exception.ExceptionConfiguration
import com.tencent.bkrepo.common.artifact.health.ArtifactHealhConfiguration
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetricsConfiguration
import com.tencent.bkrepo.common.artifact.resolve.ResolverConfiguration
import com.tencent.bkrepo.common.artifact.webhook.WebHookService
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource

@Configuration
@ConditionalOnWebApplication
@PropertySource("classpath:common-artifact.properties")
@Import(
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
