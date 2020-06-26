package com.tencent.bkrepo.replication.model

import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeCreatedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeEvent
import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.log.ResourceType
import org.omg.CORBA.Object
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("operate_log")
data class TOperateLog(
    var id: String? = null,
    var createdDate: LocalDateTime = LocalDateTime.now(),
    var resourceType: ResourceType,
    var resourceKey: String,
    var operateType: OperateType,
    var userId: String,
    var clientAddress: String,
    var description: Any
)
