package com.tencent.bkrepo.fs.server.repository.drive

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.fs.server.model.drive.TDriveSnapshot
import org.springframework.context.annotation.Conditional
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@Conditional(ReactiveCondition::class)
class RDriveSnapshotDao : DriveSimpleMongoReactiveDao<TDriveSnapshot>() {
    suspend fun page(
        projectId: String,
        repoName: String,
        pageRequest: PageRequest,
    ): Pair<List<TDriveSnapshot>, Long> {
        val criteria = prjRepoCriteria(projectId, repoName)
        val query = Query(criteria)
        val totalRecords = count(query)
        val records = find(
            query.with(pageRequest)
                .with(Sort.by(Sort.Direction.DESC, TDriveSnapshot::snapSeq.name))
        )
        return Pair(records, totalRecords)
    }

    suspend fun find(projectId: String, repoName: String, id: String): TDriveSnapshot? {
        val criteria = prjRepoCriteria(projectId, repoName).and(ID).isEqualTo(id)
        return findOne(Query(criteria))
    }

    suspend fun delete(
        projectId: String,
        repoName: String,
        id: String,
    ): DeleteResult {
        val criteria = prjRepoCriteria(projectId, repoName).and(ID).isEqualTo(id)
        return remove(Query(criteria))
    }

    suspend fun updateNameAndDescription(
        projectId: String,
        repoName: String,
        id: String,
        name: String?,
        description: String?,
        operator: String,
    ): UpdateResult {
        val criteria = prjRepoCriteria(projectId, repoName).and(ID).isEqualTo(id)
        val update = Update()
            .set(TDriveSnapshot::lastModifiedBy.name, operator)
            .set(TDriveSnapshot::lastModifiedDate.name, LocalDateTime.now())
        name?.let { update.set(TDriveSnapshot::name.name, it) }
        description?.let { update.set(TDriveSnapshot::description.name, it) }
        return updateFirst(Query(criteria), update)
    }

    private fun prjRepoCriteria(projectId: String, repoName: String): Criteria {
        return where(TDriveSnapshot::projectId).isEqualTo(projectId)
            .and(TDriveSnapshot::repoName.name).isEqualTo(repoName)
    }
}
