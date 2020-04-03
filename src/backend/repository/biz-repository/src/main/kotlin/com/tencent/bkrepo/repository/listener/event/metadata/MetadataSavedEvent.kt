package com.tencent.bkrepo.repository.listener.event.metadata

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest

data class MetadataSavedEvent (val request: MetadataSaveRequest) : MetadataEvent(request, request.operator) {
    override fun getOperateType() = OperateType.UPDATE
}
