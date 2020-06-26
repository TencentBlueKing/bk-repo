package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.replication.model.TOperateLog
import com.tencent.bkrepo.repository.listener.event.node.NodeEvent
import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.log.ResourceType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer
import org.springframework.data.mongodb.core.messaging.MessageListener
import org.springframework.data.mongodb.core.messaging.MessageListenerContainer
import org.springframework.data.mongodb.core.messaging.TailableCursorRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import javax.annotation.PostConstruct

@Service
class RealTimeJob {

    @Autowired
    private lateinit var template: MongoTemplate

    private lateinit var container: MessageListenerContainer

    private val collectionName = "operate_log"

    @PostConstruct
    fun run() {
        //while (true) {
            try {
                container = DefaultMessageListenerContainer(template)
                container.start()
                val request = getTailableCursorRequest()
                container.register(request, TOperateLog::class.java)
            } catch (exception: Exception) {
                logger.error("fail to register container [${exception.message}]")
            } finally {
                //try to sleep 10 seconds
                Thread.sleep(10000)
            }
        //}
    }


    private fun getTailableCursorRequest(): TailableCursorRequest<Any> {
        val listener = MessageListener<Document, TOperateLog> {
            val body = it.body
            body?.let {
                // val nodeEvent = body.description as NodeEvent
                // dealWithNodeEvent(nodeEvent)
                when (body.operateType) {
                    OperateType.CREATE -> {
                        body.description.toJsonString()
                        println(body.resourceType)
                        println(body.operateType)
                        dealWithNodeEvent(body.description)
                    }
                }
            }
        }
        val query = Query.query(
            Criteria.where(TOperateLog::createdDate.name).gte(LocalDateTime.now()).and(TOperateLog::resourceType.name).`is`(
                ResourceType.NODE
            ).and("description.nodeRequest.projectId").`is`(
                "ops"
            )
        )
        return TailableCursorRequest.builder()
            .collection(collectionName)
            .filter(query)
            .publishTo(listener)
            .build()
    }

    private fun dealWithNodeEvent(description: Any) {
        println(description)
        println(description.toJsonString())
        val node  = JsonUtils.objectMapper.readValue<>(description.toJsonString())
        println(node.get("request"))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RealTimeJob::class.java)
    }
}
