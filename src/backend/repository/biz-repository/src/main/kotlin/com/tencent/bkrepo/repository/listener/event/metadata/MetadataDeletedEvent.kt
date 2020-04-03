package com.tencent.bkrepo.repository.listener.event.metadata

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest

data class MetadataDeletedEvent (val request: MetadataDeleteRequest) : MetadataEvent(request, request.operator) {
    override fun getOperateType() = OperateType.UPDATE
}
