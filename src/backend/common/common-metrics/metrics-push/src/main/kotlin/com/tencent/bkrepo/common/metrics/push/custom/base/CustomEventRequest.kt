package com.tencent.bkrepo.common.metrics.push.custom.base

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

data class CustomEventRequest(
    @JsonProperty("data_id") val dataId: Long,
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("data") val data: List<CustomEventData>,
)

data class CustomEventData(
    @JsonProperty("event_name") val eventName: String,
    @JsonProperty("event") val event: CustomEventContent,
    @JsonProperty("target") val target: String,
    @JsonProperty("dimension") val dimension: Map<String, Any>,
    @JsonProperty("timestamp") val timestamp: Long,
)

data class CustomEventContent(
    @JsonProperty("content") val content: String,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("extra") val extra: Map<String, Any> = emptyMap(),
)
