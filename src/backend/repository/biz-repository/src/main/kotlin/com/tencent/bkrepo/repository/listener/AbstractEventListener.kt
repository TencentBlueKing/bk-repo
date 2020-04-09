package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.producer.StreamProducer
import com.tencent.bkrepo.repository.dao.repository.OperateLogRepository
import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.model.TOperateLog
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractEventListener {

    @Autowired
    private lateinit var operateLogRepository: OperateLogRepository

    @Autowired
    private lateinit var streamProducer: StreamProducer

    fun logEvent(event: IEvent) {
        val log = TOperateLog(
            resourceType = event.getResourceType(),
            resourceKey = event.getResourceKey(),
            operateType = event.getOperateType(),
            description = event.toString(),
            userId = event.userId,
            clientAddress = event.clientAddress
        )
        operateLogRepository.save(log)
    }

    fun sendMessage(message: IMessage) = streamProducer.sendMessage(message)
}
