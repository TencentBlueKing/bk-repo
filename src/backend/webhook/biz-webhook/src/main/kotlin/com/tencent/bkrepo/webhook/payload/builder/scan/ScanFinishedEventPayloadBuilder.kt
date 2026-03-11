package com.tencent.bkrepo.webhook.payload.builder.scan

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.webhook.payload.SubtaskEventPayload
import com.tencent.bkrepo.webhook.payload.builder.EventPayloadBuilder
import com.tencent.bkrepo.webhook.pojo.payload.CommonEventPayload
import com.tencent.bkrepo.webhook.pojo.payload.scan.ScanEventPayload
import org.springframework.stereotype.Component

@Component
class ScanFinishedEventPayloadBuilder : EventPayloadBuilder(EventType.SCAN_FINISHED) {
    override fun build(event: ArtifactEvent): CommonEventPayload {
        val payload = JsonUtils.objectMapper.convertValue(event.data["data"], SubtaskEventPayload::class.java)
        return ScanEventPayload(getUser(event.userId), EventType.SCAN_FINISHED,  payload)
    }
}