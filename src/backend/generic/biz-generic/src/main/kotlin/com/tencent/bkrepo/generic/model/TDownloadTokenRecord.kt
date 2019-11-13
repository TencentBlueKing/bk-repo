package com.tencent.bkrepo.generic.model

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 蓝盾下载Token
 */
@Document("block_record")
data class TDownloadTokenRecord(
    var id: String? = null,
    var token: String? = null,
    var projectId: String,
    val repoName: String,
    var path: String,
    var user: String,
    var downloadUser: String,
    var isInternal: Boolean,
//    var DownloadAllTimes: Int,
//    var downloadLeftTimes: Int,
    var expireTime: LocalDateTime,
    var createTime: LocalDateTime,
    var updateTime: LocalDateTime
)
