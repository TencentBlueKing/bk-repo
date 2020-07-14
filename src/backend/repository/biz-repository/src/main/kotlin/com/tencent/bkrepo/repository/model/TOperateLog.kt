package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.log.ResourceType
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("operation_log")
data class TOperateLog(
    var id: String? = null,
    var createdDate: LocalDateTime = LocalDateTime.now(),
    var resourceType: ResourceType,
    var resourceKey: String,
    var operateType: OperateType,
    var userId: String,
    var clientAddress: String,
    var description: Map<String, Any>
)
