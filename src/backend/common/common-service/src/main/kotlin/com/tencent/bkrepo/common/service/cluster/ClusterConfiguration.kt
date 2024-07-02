package com.tencent.bkrepo.common.service.cluster

import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener

@Configuration
@EnableConfigurationProperties(ClusterProperties::class)
@Import(StandaloneJobAspect::class)
class ClusterConfiguration {

    @EventListener(ApplicationStartedEvent::class)
    fun logClusterProperties() {
        val clusterProperties = SpringContextUtils.getBean<ClusterProperties>()
        with(clusterProperties) {
            logger.info("cluster start with properties: role=$role, region=$region, architecture=$architecture, " +
                "center.name=${center.name}, center.url=${center.url} " +
                "self.name=${self.name}, self.url=${self.url}")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterConfiguration::class.java)
    }
}
