package com.tencent.bkrepo.fs.server.repository.drive

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.pojo.drive.DriveMetadataQueryRule
import com.tencent.bkrepo.common.metadata.pojo.drive.DriveNameQueryRule
import com.tencent.bkrepo.common.metadata.util.drive.DriveNodeDaoHelper
import com.tencent.bkrepo.common.metadata.util.drive.DriveNodeSeriesCountHelper
import org.springframework.context.annotation.Conditional
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.mapping.Field
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

    suspend fun searchPage(
        projectId: String,
        repoName: String,
        pageSize: Int,
        name: DriveNameQueryRule? = null,
        metadata: List<DriveMetadataQueryRule> = emptyList(),
        lastModifiedDate: LocalDateTime? = null,
        lastId: String? = null,
        direction: Sort.Direction = Sort.Direction.DESC,
    ): List<TDriveNode> {
        val criteria = DriveNodeDaoHelper.searchCriteria(
            projectId = projectId,
            repoName = repoName,
            name = name,
            metadata = metadata,
        )
        appendCursorCondition(
            criteria = criteria,
            sortField = TDriveNode::lastModifiedDate.name,
            lastValue = lastModifiedDate,
            lastId = lastId,
            direction = direction,
        )
        return findCursorPage(criteria, TDriveNode::lastModifiedDate.name, pageSize, direction)
    }

    suspend fun searchCount(
        projectId: String,
        repoName: String,
        name: DriveNameQueryRule? = null,
        metadata: List<DriveMetadataQueryRule> = emptyList(),
    ): Long {
        val criteria = DriveNodeDaoHelper.searchCriteria(
            projectId = projectId,
            repoName = repoName,
            name = name,
            metadata = metadata,
        )
        return count(Query(criteria))
    }

    suspend fun searchGroupByMetadataCount(
        projectId: String,
        repoName: String,
        name: DriveNameQueryRule? = null,
        metadata: List<DriveMetadataQueryRule> = emptyList(),
        groupByMetadataKey: String,
    ): List<MetadataGroupCount> {
        val matchCriteria = DriveNodeDaoHelper.searchCriteria(
            projectId = projectId,
            repoName = repoName,
            name = name,
            metadata = metadata,
        )
        val aggregation = DriveNodeSeriesCountHelper.buildFileGroupByAggregation(
            matchCriteria = matchCriteria,
            groupByMetadataKey = groupByMetadataKey,
        )
        return mapGroupCountResults(aggregate(aggregation, SeriesGroupResult::class.java))
    }

    suspend fun searchDistinctSeriesCount(
        projectId: String,
        repoName: String,
        name: DriveNameQueryRule? = null,
        metadata: List<DriveMetadataQueryRule> = emptyList(),
        distinctByMetadataKeys: List<String>,
        latestVersionFilterKey: String? = null,
    ): Long {
        val (latestFilterRules, matchMetadata) = if (latestVersionFilterKey != null) {
            DriveNodeSeriesCountHelper.splitMetadataForSeriesCount(metadata, latestVersionFilterKey)
        } else {
            emptyList<DriveMetadataQueryRule>() to metadata
        }
        val matchCriteria = DriveNodeDaoHelper.searchCriteria(
            projectId = projectId,
            repoName = repoName,
            name = name,
            metadata = matchMetadata,
        )
        val aggregation = DriveNodeSeriesCountHelper.buildAggregation(
            matchCriteria = matchCriteria,
            distinctByMetadataKeys = distinctByMetadataKeys,
            latestVersionFilterKey = latestVersionFilterKey,
            latestFilterRules = latestFilterRules,
        )
        val results = aggregate(aggregation, SeriesCountResult::class.java)
        return results.firstOrNull()?.total ?: 0L
    }

    suspend fun searchDistinctSeriesGroupCount(
        projectId: String,
        repoName: String,
        name: DriveNameQueryRule? = null,
        metadata: List<DriveMetadataQueryRule> = emptyList(),
        distinctByMetadataKeys: List<String>,
        latestVersionFilterKey: String? = null,
        groupByMetadataKey: String,
    ): List<MetadataGroupCount> {
        val (latestFilterRules, matchMetadata) = if (latestVersionFilterKey != null) {
            DriveNodeSeriesCountHelper.splitMetadataForSeriesCount(metadata, latestVersionFilterKey)
        } else {
            emptyList<DriveMetadataQueryRule>() to metadata
        }
        val matchCriteria = DriveNodeDaoHelper.searchCriteria(
            projectId = projectId,
            repoName = repoName,
            name = name,
            metadata = matchMetadata,
        )
        val aggregation = DriveNodeSeriesCountHelper.buildAggregation(
            matchCriteria = matchCriteria,
            distinctByMetadataKeys = distinctByMetadataKeys,
            latestVersionFilterKey = latestVersionFilterKey,
            latestFilterRules = latestFilterRules,
            groupByMetadataKey = groupByMetadataKey,
        )
        return mapGroupCountResults(aggregate(aggregation, SeriesGroupResult::class.java))
    }

    private fun mapGroupCountResults(results: List<SeriesGroupResult>): List<MetadataGroupCount> {
        val merged = linkedMapOf<Any?, Long>()
        for (row in results) {
            val value = normalizeGroupValue(row.id)
            merged[value] = (merged[value] ?: 0L) + row.count
        }
        return merged.map { (value, count) ->
            MetadataGroupCount(value = value, count = count)
        }
    }

    private fun normalizeGroupValue(value: Any?): Any? {
        if (value == null) return null
        if (value is String && value.isBlank()) return null
        return value
    }

    private data class SeriesCountResult(val total: Long = 0L)

    private data class SeriesGroupResult(
        @Field("_id")
        val id: Any? = null,
        val count: Long = 0L,
    )

    /**
     * 元数据分桶计数（DAO 层中间结果）
     */
    data class MetadataGroupCount(
        /**
         * 分桶元数据值；缺失或空白时为 null
         */
        val value: Any?,
        /**
         * 该桶数量
         */
        val count: Long,
    )

    suspend fun findByProjectIdAndRepoNameAndId(projectId: String, repoName: String, id: String): TDriveNode? {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(ID).isEqualTo(id)
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
            .and(TDriveNode::deleted).isNull()
        return findOne(Query(criteria))
    }

    suspend fun findByProjectIdAndRepoNameAndIno(
        projectId: String,
        repoName: String,
        ino: Long,
        snapSeq: Long? = null,
    ): TDriveNode? {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(TDriveNode::ino).isEqualTo(ino)
        if (snapSeq == null) {
            criteria.and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
                .and(TDriveNode::deleted).isNull()
        } else {
            criteria.and(TDriveNode::snapSeq).lte(snapSeq)
                .and(TDriveNode::deleteSnapSeq).gt(snapSeq)
        }
        return findOne(Query(criteria))
    }

    suspend fun findCurrentNode(projectId: String, repoName: String, parent: Long, name: String): TDriveNode? {
        val query = Query(currentParentNameCriteria(projectId, repoName, parent, name))
        return findOne(query)
    }

    suspend fun findSnapshotNode(
        projectId: String,
        repoName: String,
        parent: Long,
        name: String,
        snapSeq: Long?,
    ): TDriveNode? {
        val query = Query(snapshotParentNameCriteria(projectId, repoName, parent, name, snapSeq))
        return findOne(query)
    }

    suspend fun existsIno(projectId: String, repoName: String, ino: Long): Boolean {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(TDriveNode::ino).isEqualTo(ino)
        return exists(Query(criteria))
    }

    suspend fun markNodeDeleted(
        projectId: String,
        repoName: String,
        id: String,
        snapSeq: Long,
        lastModifiedClientId: String? = null,
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
            .set(TDriveNode::lastModifiedClientId.name, lastModifiedClientId)
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
            .set(TDriveNode::metadata.name, updatedNode.metadata)
            .set(TDriveNode::lastModifiedBy.name, updatedNode.lastModifiedBy)
            .set(TDriveNode::lastModifiedDate.name, updatedNode.lastModifiedDate)
            .set(TDriveNode::lastModifiedClientId.name, updatedNode.lastModifiedClientId)
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

    private fun currentParentNameCriteria(projectId: String, repoName: String, parent: Long, name: String): Criteria {
        return DriveNodeDaoHelper.currentParentNameCriteria(projectId, repoName, parent, name)
    }

    private fun snapshotParentNameCriteria(
        projectId: String, repoName: String, parent: Long, name: String, snapSeq: Long? = null
    ): Criteria {
        return DriveNodeDaoHelper.listChildrenCriteria(projectId, repoName, parent, snapSeq)
            .and(TDriveNode::name.name).isEqualTo(name)
    }

    private fun listChildrenCriteria(
        projectId: String,
        repoName: String,
        parent: Long? = null,
        snapSeq: Long? = null,
    ): Criteria {
        return DriveNodeDaoHelper.listChildrenCriteria(projectId, repoName, parent, snapSeq)
    }

    private fun appendCursorCondition(
        criteria: Criteria,
        sortField: String,
        lastValue: Any?,
        lastId: String?,
        direction: Sort.Direction = Sort.Direction.ASC,
    ) {
        if (lastValue == null || lastId == null) return
        val valueCriteria = if (direction.isAscending) {
            Criteria.where(sortField).gt(lastValue)
        } else {
            Criteria.where(sortField).lt(lastValue)
        }
        val sameValueCriteria = if (direction.isAscending) {
            Criteria.where(sortField).isEqualTo(lastValue).and(ID).gt(lastId)
        } else {
            Criteria.where(sortField).isEqualTo(lastValue).and(ID).lt(lastId)
        }
        criteria.orOperator(valueCriteria, sameValueCriteria)
    }

    private suspend fun findCursorPage(
        criteria: Criteria,
        sortField: String,
        pageSize: Int,
        direction: Sort.Direction = Sort.Direction.ASC,
    ): List<TDriveNode> {
        val sort = Sort.by(direction, sortField).and(Sort.by(direction, ID))
        return find(Query(criteria).with(sort).limit(pageSize))
    }
}
