package com.tencent.bkrepo.repository.listener.event.project

import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.pojo.log.ResourceType
import com.tencent.bkrepo.repository.pojo.project.ProjectRequest

abstract class ProjectEvent(
    open val projectRequest: ProjectRequest,
    open val operator: String
) : IEvent(operator) {
    override fun getResourceType() = ResourceType.PROJECT
    override fun getResourceKey() = projectRequest.name
}
