package com.tencent.bkrepo.common.stream.binder.memory

import com.tencent.bkrepo.common.stream.binder.memory.config.MemoryBinderConfigurationProperties
import com.tencent.bkrepo.common.stream.binder.memory.config.MemoryProducerProperties
import com.tencent.bkrepo.common.stream.binder.memory.queue.MemoryMessageQueue
import org.springframework.cloud.stream.binder.ExtendedProducerProperties
import org.springframework.cloud.stream.provisioning.ProducerDestination
import org.springframework.context.Lifecycle
import org.springframework.integration.handler.AbstractMessageHandler
import org.springframework.messaging.Message

class MemoryMessageHandler(
    private val configurationProperties: MemoryBinderConfigurationProperties,
    private val destination: ProducerDestination,
    private val producerProperties: ExtendedProducerProperties<MemoryProducerProperties>
) : AbstractMessageHandler(), Lifecycle {

    @Volatile
    private var started = false

    override fun handleMessageInternal(message: Message<*>?) {
        if (message != null) {
            MemoryMessageQueue.instance.produce(this.destination.name, message)
        }
    }

    override fun isRunning(): Boolean = started

    override fun start() {
        if (!started) {
            started = true
            MemoryMessageQueue.instance.start(
                configurationProperties.queueSize,
                configurationProperties.workerPoolSize
            )
        }
    }

    override fun stop() {
        if (started) {
            started = false
            MemoryMessageQueue.instance.shutdown()
        }
    }
}
