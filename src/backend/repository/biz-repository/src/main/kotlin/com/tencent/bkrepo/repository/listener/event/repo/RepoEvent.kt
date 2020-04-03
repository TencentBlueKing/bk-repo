package com.tencent.bkrepo.repository.listener.event.repo

import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.pojo.log.ResourceType
import com.tencent.bkrepo.repository.pojo.repo.RepoRequest

abstract class RepoEvent(
    open val repoRequest: RepoRequest,
    open val operator: String
) : IEvent(operator) {
    override fun getResourceType() = ResourceType.REPOSITORY
    override fun getResourceKey() = "/${repoRequest.projectId}/${repoRequest.name}"
}
