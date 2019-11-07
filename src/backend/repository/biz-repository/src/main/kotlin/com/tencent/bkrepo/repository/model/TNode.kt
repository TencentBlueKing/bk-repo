package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingDocument
import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingKey
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

/**
 * 资源模型
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@ShardingDocument("node")
@CompoundIndexes(
        CompoundIndex(name = "projectId_repoName_fullPath_idx", def = "{'projectId': 1, 'repoName': 1, 'fullPath': 1, 'deleted': 1}", unique = true, background = true),
        CompoundIndex(name = "projectId_repoName_path_idx", def = "{'projectId': 1, 'repoName': 1, 'path': 1, 'deleted': 1}", background = true)
)
data class TNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var folder: Boolean,
    var path: String,
    var name: String,
    var fullPath: String,
    var size: Long,
    var expireDate: LocalDateTime? = null,
    var sha256: String? = null,
    var deleted: LocalDateTime? = null,
    var metadata: Map<String, String>? = null,
    var blockList: List<TFileBlock>? = null,

    @ShardingKey(count = 256)
    var projectId: String,
    var repoName: String
)
