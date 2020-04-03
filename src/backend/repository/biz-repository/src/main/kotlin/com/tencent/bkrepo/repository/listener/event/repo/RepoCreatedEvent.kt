package com.tencent.bkrepo.repository.listener.event.repo

import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.log.OperateType

class RepoCreatedEvent(
    override val repo: TRepository,
    override val operator: String
) : RepoEvent(repo, operator) {
    override fun getOperateType() = OperateType.CREATE
}
