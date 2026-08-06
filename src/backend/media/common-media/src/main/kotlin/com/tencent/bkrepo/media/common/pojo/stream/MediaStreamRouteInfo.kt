package com.tencent.bkrepo.media.common.pojo.stream

data class MediaStreamRouteInfo(
    val streamId: String,
    val machine: String,
    val serverId: String? = null,
)
