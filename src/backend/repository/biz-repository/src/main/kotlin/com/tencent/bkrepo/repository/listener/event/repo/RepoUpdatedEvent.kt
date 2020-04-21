package com.tencent.bkrepo.repository.listener.event.repo

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest

data class RepoUpdatedEvent(val request: RepoUpdateRequest) : RepoEvent(request, request.operator) {
    override fun getOperateType() = OperateType.UPDATE
}
