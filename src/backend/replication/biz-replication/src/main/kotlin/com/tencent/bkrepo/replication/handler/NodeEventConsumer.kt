package com.tencent.bkrepo.replication.handler

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.stream.message.node.NodeCopiedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeDeletedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeMovedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeRenamedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeUpdatedMessage
import com.tencent.bkrepo.replication.config.NODE_REQUEST
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * consume event from caped collection and
 * dispatch it to event  handler
 * @author: owenlxu
 * @date: 2020/05/20
 */
@Component
class NodeEventConsumer : AbstractHandler() {

    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    fun dealWithNodeCreateEvent(description: Map<String, Any>) {
        val request = description[NODE_REQUEST] as String
        JsonUtils.objectMapper.readValue(request, NodeCreateRequest::class.java).also {
            eventPublisher.publishEvent(NodeCreatedMessage(it))
        }
    }

    fun dealWithNodeRenameEvent(description: Map<String, Any>) {
        val request = description[NODE_REQUEST] as String
        JsonUtils.objectMapper.readValue(request, NodeRenameRequest::class.java).also {
            eventPublisher.publishEvent(NodeRenamedMessage(it))
        }
    }

    fun dealWithNodeCopyEvent(description: Map<String, Any>) {
        val request = description[NODE_REQUEST] as String
        JsonUtils.objectMapper.readValue(request, NodeCopyRequest::class.java).also {
            eventPublisher.publishEvent(NodeCopiedMessage(it))
        }
    }

    fun dealWithNodeDeleteEvent(description: Map<String, Any>) {
        val request = description[NODE_REQUEST] as String
        JsonUtils.objectMapper.readValue(request, NodeDeleteRequest::class.java).also {
            eventPublisher.publishEvent(NodeDeletedMessage(it))
        }
    }

    fun dealWithNodeMoveEvent(description: Map<String, Any>) {
        val request = description[NODE_REQUEST] as String
        JsonUtils.objectMapper.readValue(request, NodeMoveRequest::class.java).also {
            eventPublisher.publishEvent(NodeMovedMessage(it))
        }
    }

    fun dealWithNodeUpdateEvent(description: Map<String, Any>) {
        val request = description["request"] as String
        JsonUtils.objectMapper.readValue(request, NodeUpdateRequest::class.java).also {
            eventPublisher.publishEvent(NodeUpdatedMessage(it))
        }
    }
}
