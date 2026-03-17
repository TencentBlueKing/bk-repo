package com.tencent.bkrepo.fs.server.config

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.fs.server.listener.DriveRepoEventConsumer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import java.util.function.Consumer

/**
 * Drive 消息队列消费者配置
 */
@Configuration
class DriveConsumerConfig {

    /**
     * 注册 Drive 仓库事件消费者
     *
     * 对应 binding name: driveRepoEventConsumer-in-0
     * 需要配置 destination 为 artifactEvent
     */
    @Bean("driveRepoEventConsumer")
    fun driveRepoEventConsumer(driveRepoEventConsumer: DriveRepoEventConsumer): Consumer<Message<ArtifactEvent>> {
        return Consumer { driveRepoEventConsumer.accept(it) }
    }
}
