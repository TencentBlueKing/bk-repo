package com.tencent.bkrepo.common.stream.message.repo

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest

data class RepoUpdatedMessage(val request: RepoUpdateRequest) : IMessage {
    override fun getMessageType() = MessageType.REPO_UPDATED
}
