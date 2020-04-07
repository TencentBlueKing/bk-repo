package com.tencent.bkrepo.common.artifact.event

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.event.StoreFailureEvent
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
        event.apply {
            LoggerHolder.sysErrorLogger.error("[StoreFailureEvent]failed to store file[$filename] on [$storageCredentials].", exception)
        }
    }

}
