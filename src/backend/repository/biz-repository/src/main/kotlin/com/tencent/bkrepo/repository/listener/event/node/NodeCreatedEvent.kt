package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.log.OperateType

data class NodeCreatedEvent (
    override val repository: TRepository,
    override val node: TNode
) : NodeEvent(repository, node) {
    override fun getOperateType() = OperateType.CREATE
}