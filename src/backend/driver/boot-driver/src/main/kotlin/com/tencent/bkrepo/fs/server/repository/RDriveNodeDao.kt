package com.tencent.bkrepo.fs.server.repository

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.mongo.reactive.dao.HashShardingMongoReactiveDao
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import org.springframework.context.annotation.Conditional
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@Conditional(ReactiveCondition::class)
class RDriveNodeDao : HashShardingMongoReactiveDao<TDriveNode>() {
    suspend fun listNode(projectId: String, repoName: String, parent: Long): List<TDriveNode> {
        val query = Query(listChildrenCriteria(projectId, repoName, parent))
        return find(query)
    }

    suspend fun existsChild(projectId: String, repoName: String, parent: Long): Boolean {
        return exists(Query(listChildrenCriteria(projectId, repoName, parent)))
    }

    suspend fun nodePage(
        projectId: String,
        repoName: String,
        parent: Long?,
        pageRequest: PageRequest,
        includeTotalRecords: Boolean = false,
        snapSeq: Long? = null,
    ): Pair<List<TDriveNode>, Long> {
        val criteria = listChildrenCriteria(projectId, repoName, parent, snapSeq)
        val totalRecords = if (includeTotalRecords) count(Query(criteria)) else 0L
        val records = find(Query(criteria).with(pageRequest))
        return Pair(records, totalRecords)
    }

    suspend fun modifiedNodePage(
        projectId: String,
        repoName: String,
        lastModifiedDate: LocalDateTime,
        pageRequest: PageRequest,
        includeTotalRecords: Boolean = false,
    ): Pair<List<TDriveNode>, Long> {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(TDriveNode::lastModifiedDate).gt(lastModifiedDate)
        val totalRecords = if (includeTotalRecords) count(Query(criteria)) else 0L
        val records = find(Query(criteria).with(pageRequest))
        return Pair(records, totalRecords)
    }

    suspend fun findByProjectIdAndRepoNameAndId(projectId: String, repoName: String, id: String): TDriveNode? {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(ID).isEqualTo(id)
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
            .and(TDriveNode::deleted).isNull()
        return findOne(Query(criteria))
    }

    suspend fun findByProjectIdAndRepoNameAndIno(projectId: String, repoName: String, ino: Long): TDriveNode? {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(TDriveNode::ino).isEqualTo(ino)
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
            .and(TDriveNode::deleted).isNull()
        return findOne(Query(criteria))
    }

    suspend fun findCurrentNode(projectId: String, repoName: String, parent: Long, name: String): TDriveNode? {
        val query = Query(currentParentNameCriteria(projectId, repoName, parent, name))
        return findOne(query)
    }

    suspend fun markNodeDeleted(
        projectId: String,
        repoName: String,
        id: String,
        snapSeq: Long,
        lastModifiedDate: LocalDateTime? = null
    ): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(id)
            .and(TDriveNode::projectId.name).isEqualTo(projectId)
            .and(TDriveNode::repoName.name).isEqualTo(repoName)
            .and(TDriveNode::deleted).isNull()
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
        lastModifiedDate?.let { criteria.and(TDriveNode::lastModifiedDate).isEqualTo(it) }
        val query = Query(criteria)
        val now = LocalDateTime.now()
        val update = Update()
            .set(TDriveNode::lastModifiedDate.name, now)
            .set(TDriveNode::deleteSnapSeq.name, snapSeq)
            .set(TDriveNode::deleted.name, now)
        return updateFirst(query, update)
    }

    suspend fun updateByNodeId(
        projectId: String,
        repoName: String,
        nodeId: String,
        lastModifiedDate: LocalDateTime? = null,
        updatedNode: TDriveNode
    ): UpdateResult {
        val criteria = currentNodeIdCriteria(projectId, repoName, nodeId)
        lastModifiedDate?.let { criteria.and(TDriveNode::lastModifiedDate).isEqualTo(it) }
        val update = Update()
            .set(TDriveNode::parent.name, requireNotNull(updatedNode.parent))
            .set(TDriveNode::name.name, updatedNode.name)
            .set(TDriveNode::size.name, updatedNode.size)
            .set(TDriveNode::mode.name, updatedNode.mode)
            .set(TDriveNode::nlink.name, updatedNode.nlink)
            .set(TDriveNode::uid.name, updatedNode.uid)
            .set(TDriveNode::gid.name, updatedNode.gid)
            .set(TDriveNode::rdev.name, updatedNode.rdev)
            .set(TDriveNode::flags.name, updatedNode.flags)
            .set(TDriveNode::symlinkTarget.name, updatedNode.symlinkTarget)
            .set(TDriveNode::mtime.name, updatedNode.mtime)
            .set(TDriveNode::ctime.name, updatedNode.ctime)
            .set(TDriveNode::atime.name, updatedNode.atime)
            .set(TDriveNode::lastModifiedBy.name, updatedNode.lastModifiedBy)
            .set(TDriveNode::lastModifiedDate.name, updatedNode.lastModifiedDate)
        return updateFirst(
            Query(criteria),
            update
        )
    }

    private fun currentNodeIdCriteria(projectId: String, repoName: String, nodeId: String): Criteria {
        return where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(ID).isEqualTo(nodeId)
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
            .and(TDriveNode::deleted).isNull()
    }

    /**
     * 查当前活跃节点
     */
    private fun currentParentNameCriteria(projectId: String, repoName: String, parent: Long, name: String): Criteria {
        return snapshotParentNameCriteria(projectId, repoName, parent, name)
    }

    /**
     * 查快照视图节点
     *
     * 条件：snapSeq <= targetSnapSeq AND deleteSnapSeq > targetSnapSeq
     */
    private fun snapshotParentNameCriteria(
        projectId: String, repoName: String, parent: Long, name: String, snapSeq: Long? = null
    ): Criteria {
        return listChildrenCriteria(projectId, repoName, parent, snapSeq).and(TDriveNode::name).isEqualTo(name)
    }

    /**
     * 列出子节点
     *
     * @param snapSeq 快照序列号，null 时查当前视图
     */
    private fun listChildrenCriteria(
        projectId: String,
        repoName: String,
        parent: Long? = null,
        snapSeq: Long? = null,
    ): Criteria {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName.name).isEqualTo(repoName)
        parent?.let { criteria.and(TDriveNode::parent).isEqualTo(it) }
        return if (snapSeq == null) {
            criteria.and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
                .and(TDriveNode::deleted).isEqualTo(null)
        } else {
            criteria.and(TDriveNode::snapSeq).lte(snapSeq)
                .and(TDriveNode::deleteSnapSeq).gt(snapSeq)
        }
    }
}
