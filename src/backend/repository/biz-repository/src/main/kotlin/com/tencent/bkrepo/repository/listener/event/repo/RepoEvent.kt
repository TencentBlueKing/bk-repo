package com.tencent.bkrepo.repository.listener.event.repo

import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.log.ResourceType

abstract class RepoEvent(
    open val repo: TRepository,
    open val operator: String
) : IEvent(operator) {
    override fun getResourceType() = ResourceType.REPOSITORY
    override fun getResourceKey() = "/${repo.projectId}/${repo.name}"
}
