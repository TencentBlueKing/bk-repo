package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingDocument
import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingKey
import com.tencent.bkrepo.repository.constant.NodeType
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.model.TNode.Companion.NODE_FULL_PATH_IDX
import com.tencent.bkrepo.repository.model.TNode.Companion.NODE_FULL_PATH_IDX_DEF
import com.tencent.bkrepo.repository.model.TNode.Companion.NODE_IDX
import com.tencent.bkrepo.repository.model.TNode.Companion.NODE_IDX_DEF
import com.tencent.bkrepo.repository.model.TNode.Companion.NODE_PATH_IDX
import com.tencent.bkrepo.repository.model.TNode.Companion.NODE_PATH_IDX_DEF
import com.tencent.bkrepo.repository.model.TNode.Companion.PACKAGE_IDX
import com.tencent.bkrepo.repository.model.TNode.Companion.PACKAGE_IDX_DEF
import com.tencent.bkrepo.repository.model.TNode.Companion.PACKAGE_METADATA_DEF
import com.tencent.bkrepo.repository.model.TNode.Companion.PACKAGE_METADATA_IDX
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

/**
 * 资源模型
 */
@ShardingDocument("node")
@CompoundIndexes(
    CompoundIndex(name = NODE_IDX, def = NODE_IDX_DEF, background = true, unique = true),
    CompoundIndex(name = NODE_PATH_IDX, def = NODE_PATH_IDX_DEF, background = true),
    CompoundIndex(name = NODE_FULL_PATH_IDX, def = NODE_FULL_PATH_IDX_DEF, background = true),
    CompoundIndex(name = PACKAGE_IDX, def = PACKAGE_IDX_DEF, background = true),
    CompoundIndex(name = PACKAGE_METADATA_IDX, def = PACKAGE_METADATA_DEF, background = true)
)
data class TNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    @ShardingKey(count = SHARDING_COUNT)
    var projectId: String,
    var repoName: String,
    var folder: Boolean,
    var path: String,
    var name: String,
    var fullPath: String,
    var size: Long,
    var type: NodeType = NodeType.ARTIFACT,
    var packageName: String? = null,
    var packageVersion: String? = null,
    var expireDate: LocalDateTime? = null,
    var sha256: String? = null,
    var md5: String? = null,
    var metadata: MutableList<TMetadata>? = null,
    var downloads: Long = 0,
    var deleted: Long = 0
) {
    companion object {
        const val NODE_IDX = "node_metadata_idx"
        const val NODE_PATH_IDX = "node_path_idx"
        const val NODE_FULL_PATH_IDX = "node_full_path_idx"
        const val PACKAGE_IDX = "package_idx"
        const val PACKAGE_METADATA_IDX = "package_metadata_idx"

        const val NODE_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'fullPath': 1, 'deleted': 1}"
        const val NODE_PATH_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'folder': -1, 'path': 1, 'name': 1, 'metadata.key': 1, 'metadata.value': 1}"
        const val NODE_FULL_PATH_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'folder': -1, 'fullPath': 1, 'metadata.key': 1, 'metadata.value': 1}"
        const val PACKAGE_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'packageName': 1, 'packageVersion': 1, 'type': 1,}"
        const val PACKAGE_METADATA_DEF = "{'projectId': 1, 'repoName': 1, 'metadata.key': 1, 'metadata.value': 1, 'type': 1}"
    }
}
