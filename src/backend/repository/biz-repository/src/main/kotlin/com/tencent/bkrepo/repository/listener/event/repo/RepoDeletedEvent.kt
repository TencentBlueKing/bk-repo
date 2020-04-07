package com.tencent.bkrepo.repository.listener.event.repo

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest

data class RepoDeletedEvent(val request: RepoDeleteRequest) : RepoEvent(request, request.operator) {
    override fun getOperateType() = OperateType.DELETE
}
