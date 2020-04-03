package com.tencent.bkrepo.repository.listener.event.project

import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.model.TProject
import com.tencent.bkrepo.repository.pojo.log.ResourceType

abstract class ProjectEvent(
    open val project: TProject,
    open val operator: String
) : IEvent(operator) {
    override fun getResourceType() = ResourceType.PROJECT
    override fun getResourceKey() = project.name
}
