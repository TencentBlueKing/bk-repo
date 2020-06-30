package com.tencent.bkrepo.common.quartz

import com.novemberain.quartz.mongodb.cluster.CheckinTask
import com.novemberain.quartz.mongodb.dao.TriggerDao
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.boot.logging.LogLevel
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

// @Component
class QuartzEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication?) {
        if (!environment.propertySources.contains(QUARTZ_DEFAULT_PROPERTIES)) {
            val replicationDefaultProperties: MutableMap<String, Any> = HashMap()
            replicationDefaultProperties["logging.level.${CheckinTask::class.java.name}"] = LogLevel.WARN.name
            replicationDefaultProperties["logging.level.${TriggerDao::class.java.name}"] = LogLevel.WARN.name
            environment.propertySources.addLast(MapPropertySource(QUARTZ_DEFAULT_PROPERTIES, replicationDefaultProperties))
        }
    }

    companion object {
        private const val QUARTZ_DEFAULT_PROPERTIES = "quartzDefaultProperties"
    }
}
