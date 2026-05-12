package com.tencent.bkrepo.media.common.model

import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 活跃流记录
 * 用于记录当前可被拉取的流以及所在机器
 */
@Document("media_active_stream")
data class TMediaActiveStream(
    var id: String? = null,
    @Indexed(name = "media_active_stream_stream_id_idx", unique = true, background = true)
    var streamId: String,
    var machine: String,
    var serverId: String? = null,
    var app: String? = null,
    var vhost: String? = null,
    var clientIp: String? = null,
    var createdTime: LocalDateTime,
    var updateTime: LocalDateTime,
)
