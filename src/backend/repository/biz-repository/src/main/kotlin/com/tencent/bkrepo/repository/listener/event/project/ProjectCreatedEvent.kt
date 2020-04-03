package com.tencent.bkrepo.repository.listener.event.project

import com.tencent.bkrepo.repository.model.TProject
import com.tencent.bkrepo.repository.pojo.log.OperateType

class ProjectCreatedEvent(
    override val project: TProject,
    override val operator: String
) : ProjectEvent(project, operator) {
    override fun getOperateType() = OperateType.CREATE
}
