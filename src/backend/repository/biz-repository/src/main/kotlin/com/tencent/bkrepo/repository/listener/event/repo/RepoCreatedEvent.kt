package com.tencent.bkrepo.repository.listener.event.repo

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest

class RepoCreatedEvent(val request: RepoCreateRequest) : RepoEvent(request, request.operator) {
    override fun getOperateType() = OperateType.CREATE
}
