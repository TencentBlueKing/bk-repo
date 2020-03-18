package com.tencent.bkrepo.replication.config

import com.novemberain.quartz.mongodb.cluster.CheckinTask
import com.novemberain.quartz.mongodb.dao.TriggerDao
import com.tencent.bkrepo.common.artifact.auth.basic.BasicClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.ClientAuthConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.logging.LogLevel
import org.springframework.boot.logging.LoggingSystem
import org.springframework.cloud.openfeign.FeignClientsConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
@Configuration
@Import(FeignClientsConfiguration::class)
class ReplicationConfiguration : ArtifactConfiguration {

    @Autowired
    private lateinit var loggingSystem: LoggingSystem

    @PostConstruct
    fun init() {
        loggingSystem.setLogLevel(CheckinTask::class.java.name, LogLevel.WARN)
        loggingSystem.setLogLevel(TriggerDao::class.java.name, LogLevel.WARN)
    }

    override
    fun getClientAuthConfig() = ClientAuthConfig(excludePatterns = listOf("/cluster/ping", "/cluster/version"))
}
