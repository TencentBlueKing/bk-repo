package com.tencent.bkrepo.repository.listener.event.project

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest

data class ProjectCreatedEvent(val request: ProjectCreateRequest) : ProjectEvent(request, request.operator) {
    override fun getOperateType() = OperateType.CREATE
}
