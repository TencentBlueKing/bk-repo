package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingDocument
import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingKey
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.model.TNode.Companion.FULL_PATH_IDX
import com.tencent.bkrepo.repository.model.TNode.Companion.FULL_PATH_IDX_DEF
import com.tencent.bkrepo.repository.model.TNode.Companion.METADATA_IDX
import com.tencent.bkrepo.repository.model.TNode.Companion.METADATA_IDX_DEF
import com.tencent.bkrepo.repository.model.TNode.Companion.PATH_IDX
import com.tencent.bkrepo.repository.model.TNode.Companion.PATH_IDX_DEF
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
    CompoundIndex(name = FULL_PATH_IDX, def = FULL_PATH_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = PATH_IDX, def = PATH_IDX_DEF, background = true),
    CompoundIndex(name = METADATA_IDX, def = METADATA_IDX_DEF, background = true)
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
    var md5: String? = null,
    var deleted: LocalDateTime? = null,
    var metadata: List<TMetadata>? = null,

    @ShardingKey(count = SHARDING_COUNT)
    var projectId: String,
    var repoName: String
) {
    companion object {
        const val FULL_PATH_IDX = "projectId_repoName_fullPath_idx"
        const val PATH_IDX = "projectId_repoName_path_idx"
        const val METADATA_IDX = "metadata_idx"
        const val FULL_PATH_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'fullPath': 1, 'deleted': 1}"
        const val PATH_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'path': 1, 'deleted': 1}"
        const val METADATA_IDX_DEF = "{'metadata.key': 1, 'metadata.value': 1}"
    }
}
