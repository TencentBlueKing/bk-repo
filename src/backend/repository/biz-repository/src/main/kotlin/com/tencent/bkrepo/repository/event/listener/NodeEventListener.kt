package com.tencent.bkrepo.repository.event.listener

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.repository.event.node.NodeCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class NodeEventListener {

    @EventListener(NodeCreatedEvent::class)
    fun listen(event: NodeCreatedEvent) {
        logger.debug("Receive event[$event]")
        // 查询是否需要同步
        with(event.repositoryInfo) {
            if (category == RepositoryCategory.LOCAL) {
                val configuration = event.repositoryInfo.configuration as LocalConfiguration
                configuration.webHookConfiguration
            }
        }

        // 创建消息payload
        // 发送消息
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeEventListener::class.java)
    }
}
