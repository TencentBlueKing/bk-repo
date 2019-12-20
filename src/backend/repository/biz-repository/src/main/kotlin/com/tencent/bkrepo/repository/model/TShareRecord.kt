package com.tencent.bkrepo.repository.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 构件共享记录
 *
 * @author: carrypan
 * @date: 2019/12/20
 */
@Document("share_record")
@CompoundIndexes(
    CompoundIndex(def = "{'projectId': 1, 'repoName': 1, 'fullPath': 1}", background = true)
)
data class TShareRecord (
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var projectId: String,
    var repoName: String,
    var fullPath: String,
    var token: String,
    var authorizedUserList: List<String>,
    var authorizedIpList: List<String>,
    var expireDate: LocalDateTime ?= null
)