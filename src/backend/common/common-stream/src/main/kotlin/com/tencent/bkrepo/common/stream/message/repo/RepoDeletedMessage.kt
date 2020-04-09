package com.tencent.bkrepo.common.stream.message.repo

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest

data class RepoDeletedMessage(val request: RepoDeleteRequest) : IMessage {
    override fun getMessageType() = MessageType.REPO_DELETED
}
