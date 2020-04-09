package com.tencent.bkrepo.common.stream.message.project

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest

data class ProjectCreatedMessage(val request: ProjectCreateRequest) : IMessage {
    override fun getMessageType() = MessageType.PROJECT_CREATED
}
