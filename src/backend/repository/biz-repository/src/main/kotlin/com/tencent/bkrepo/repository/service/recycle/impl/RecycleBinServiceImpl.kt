package com.tencent.bkrepo.repository.service.recycle.impl

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ROOT_DELETED_NODE
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.util.MetadataUtils.buildExpiredDeletedNodeMetadata
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper.nodeDeletedPointQuery
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper.nodeDeletedQuery
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper.nodeTreeCriteria
import com.tencent.bkrepo.repository.service.recycle.RecycleBinService
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

@Service
@Conditional(SyncCondition::class)
class RecycleBinServiceImpl(
    private val nodeDao: NodeDao,
) : RecycleBinService {
    override fun irreversibleDelete(artifactInfo: ArtifactInfo, deletedId: Long) {
        with(artifactInfo) {
            val deletedTime = Instant.ofEpochMilli(deletedId).atZone(ZoneId.systemDefault()).toLocalDateTime()
            nodeDao.updateFirst(
                nodeDeletedPointQuery(projectId, repoName, getArtifactFullPath(), deletedTime),
                Update().pull(TNode::metadata.name, Query(where(TMetadata::key).isEqualTo(ROOT_DELETED_NODE)))
            )
            nodeDao.updateMulti(
                Query(nodeTreeCriteria(projectId, repoName, getArtifactFullPath(), deletedTime)),
                Update().push(TNode::metadata.name, buildExpiredDeletedNodeMetadata())
            )
        }
    }

    override fun clean(projectId: String, repoName: String) {
        nodeDao.updateMulti(
            Query(
                where(TNode::projectId).isEqualTo(projectId).and(TNode::repoName.name).isEqualTo(repoName)
                    .and("${TNode::metadata.name}.${TMetadata::key.name}").isEqualTo(ROOT_DELETED_NODE)
            ),
            Update().pull(TNode::metadata.name, Query(where(TMetadata::key).isEqualTo(ROOT_DELETED_NODE)))
        )
        nodeDao.updateMulti(
            nodeDeletedQuery(projectId, repoName),
            Update().push(TNode::metadata.name, buildExpiredDeletedNodeMetadata())
        )
    }

    override fun irreversibleDeleteGeneric(artifactInfo: ArtifactInfo, deletedId: Long) {
        TODO("Not yet implemented")
    }

    override fun irreversibleDeletePackage(artifactInfo: ArtifactInfo, deletedId: Long) {
        TODO("Not yet implemented")
    }
}
