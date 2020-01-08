package com.tencent.bkrepo.common.artifact.event

import com.tencent.bkrepo.common.storage.event.StoreFailureEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener

/**
 * 构件相关事件监听器
 *
 * @author: carrypan
 * @date: 2020/1/8
 */
class ArtifactEventListener {

    @EventListener(StoreFailureEvent::class)
    fun listen(event: StoreFailureEvent) {
        logger.info("Receive event[$event]")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactEventListener::class.java)
    }
}
