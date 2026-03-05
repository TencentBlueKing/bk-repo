package com.tencent.bkrepo.fs.server.repository

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
    suspend fun listNode(projectId: String, repoName: String, parent: String): List<TDriveNode> {
        val query = Query(listChildrenCriteria(projectId, repoName, parent))
        return find(query)
    }

    suspend fun existsChild(projectId: String, repoName: String, parent: String): Boolean {
        return exists(Query(listChildrenCriteria(projectId, repoName, parent)))
    }

    suspend fun nodePage(
        projectId: String,
        repoName: String,
        parent: String,
        pageRequest: PageRequest,
        includeTotalRecords: Boolean = false,
    ): Pair<List<TDriveNode>, Long> {
        val criteria = listChildrenCriteria(projectId, repoName, parent)
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

    suspend fun findCurrentNode(projectId: String, repoName: String, parent: String, name: String): TDriveNode? {
        val query = Query(currentCriteria(projectId, repoName, parent, name))
        return findOne(query)
    }

    suspend fun sameInoCount(projectId: String, repoName: String, ino: String): Long {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(TDriveNode::ino).isEqualTo(ino)
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
            .and(TDriveNode::deleted).isNull()
        return count(Query(criteria))
    }

    suspend fun markNodeDeleted(id: String, snapSeq: Long) {
        val criteria = Criteria.where(ID).isEqualTo(id)
            .and(TDriveNode::deleted).isNull()
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
        val query = Query(criteria)
        val update = Update()
            .set(TDriveNode::deleteSnapSeq.name, snapSeq)
            .set(TDriveNode::deleted.name, LocalDateTime.now())
        updateFirst(query, update)
    }

    /**
     * 查当前活跃节点
     */
    private fun currentCriteria(projectId: String, repoName: String, parent: String, name: String): Criteria {
        return snapshotCriteria(projectId, repoName, parent, name)
    }

    /**
     * 查快照视图节点
     *
     * 条件：snapSeq <= targetSnapSeq AND deleteSnapSeq > targetSnapSeq
     */
    private fun snapshotCriteria(
        projectId: String, repoName: String, parent: String, name: String, snapSeq: Long? = null
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
        parent: String,
        snapSeq: Long? = null,
    ): Criteria {
        return if (snapSeq == null) {
            where(TDriveNode::projectId).isEqualTo(projectId)
                .and(TDriveNode::repoName.name).isEqualTo(repoName)
                .and(TDriveNode::parent).isEqualTo(parent)
                .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
                .and(TDriveNode::deleted).isEqualTo(null)
        } else {
            where(TDriveNode::projectId).isEqualTo(projectId)
                .and(TDriveNode::repoName).isEqualTo(repoName)
                .and(TDriveNode::parent).isEqualTo(parent)
                .and(TDriveNode::snapSeq).lte(snapSeq)
                .and(TDriveNode::deleteSnapSeq).gt(snapSeq)
        }
    }
}
