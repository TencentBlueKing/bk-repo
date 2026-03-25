package com.tencent.bkrepo.webhook.pojo.payload.scan

import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.webhook.payload.SubtaskEventPayload
import com.tencent.bkrepo.webhook.pojo.payload.CommonEventPayload

data class ScanEventPayload(
    override val user: UserInfo,
    override val eventType: EventType,
    val task: SubtaskEventPayload,
) : CommonEventPayload(eventType = eventType, user = user,)
