package com.tencent.bkrepo.fs.server.repository.drive

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import org.springframework.context.annotation.Conditional
import org.springframework.data.domain.Sort
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
class RDriveNodeDao : DriveHashShardingMongoReactiveDao<TDriveNode>() {
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
        pageSize: Int,
        lastName: String? = null,
        lastId: String? = null,
        snapSeq: Long? = null,
    ): List<TDriveNode> {
        val criteria = listChildrenCriteria(projectId, repoName, parent, snapSeq)
        appendCursorCondition(criteria, TDriveNode::name.name, lastName, lastId)
        return findCursorPage(criteria, TDriveNode::name.name, pageSize)
    }

    suspend fun modifiedNodePage(
        projectId: String,
        repoName: String,
        pageSize: Int,
        lastModifiedDate: LocalDateTime,
        lastId: String,
    ): List<TDriveNode> {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
        appendCursorCondition(criteria, TDriveNode::lastModifiedDate.name, lastModifiedDate, lastId)
        return findCursorPage(criteria, TDriveNode::lastModifiedDate.name, pageSize)
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
        ifMatch: LocalDateTime? = null
    ): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(id)
            .and(TDriveNode::projectId.name).isEqualTo(projectId)
            .and(TDriveNode::repoName.name).isEqualTo(repoName)
            .and(TDriveNode::deleted).isNull()
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
        ifMatch?.let { criteria.and(TDriveNode::lastModifiedDate).isEqualTo(it) }
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
        ifMatch: LocalDateTime? = null,
        updatedNode: TDriveNode
    ): UpdateResult {
        val criteria = currentNodeIdCriteria(projectId, repoName, nodeId)
        ifMatch?.let { criteria.and(TDriveNode::lastModifiedDate).isEqualTo(it) }
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

    private fun appendCursorCondition(criteria: Criteria, sortField: String, lastValue: Any?, lastId: String?) {
        if (lastValue == null || lastId == null) return
        criteria.orOperator(
            Criteria.where(sortField).gt(lastValue),
            Criteria.where(sortField).isEqualTo(lastValue).and(ID).gt(lastId),
        )
    }

    private suspend fun findCursorPage(criteria: Criteria, sortField: String, pageSize: Int): List<TDriveNode> {
        val sort = Sort.by(Sort.Direction.ASC, sortField).and(Sort.by(Sort.Direction.ASC, ID))
        return find(Query(criteria).with(sort).limit(pageSize))
    }
}
