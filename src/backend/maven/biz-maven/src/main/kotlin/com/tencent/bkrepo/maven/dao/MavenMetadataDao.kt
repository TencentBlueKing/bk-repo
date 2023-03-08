package com.tencent.bkrepo.maven.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.maven.model.TMavenMetadataRecord
import com.tencent.bkrepo.maven.pojo.metadata.MavenMetadataRequest
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class MavenMetadataDao : SimpleMongoDao<TMavenMetadataRecord>() {

    fun findAndModify(
        request: MavenMetadataRequest,
        incBuildNo: Boolean,
        upsert: Boolean,
        returnNew: Boolean
    ): TMavenMetadataRecord? {
        val query = Query(buildCriteria(request))
        val options = FindAndModifyOptions().upsert(upsert).returnNew(returnNew)
        val update = Update().set(TMavenMetadataRecord::timestamp.name, request.timestamp)
        if (incBuildNo) {
            update.inc(TMavenMetadataRecord::buildNo.name)
        } else {
            update.set(TMavenMetadataRecord::buildNo.name, request.buildNo ?: 0)
        }

        return determineMongoTemplate().findAndModify(
            query, update, options, TMavenMetadataRecord::class.java
        )
    }

    fun delete(request: MavenMetadataRequest) {
        remove(Query(buildCriteria(request)))
    }

    private fun buildCriteria(request: MavenMetadataRequest): Criteria {
        with(request) {
            val criteria = Criteria
                .where(TMavenMetadataRecord::projectId.name).`is`(projectId)
                .and(TMavenMetadataRecord::repoName.name).`is`(repoName)
                .and(TMavenMetadataRecord::groupId.name).`is`(groupId)
                .and(TMavenMetadataRecord::artifactId.name).`is`(artifactId)
                .and(TMavenMetadataRecord::version.name).`is`(version)
            if (classifier == null) {
                criteria.and(TMavenMetadataRecord::classifier.name).exists(false)
            } else {
                criteria.and(TMavenMetadataRecord::classifier.name).`is`(classifier)
            }
            extension?.let { criteria.and(TMavenMetadataRecord::extension.name).`is`(it) }
            timestamp?.let { criteria.and(TMavenMetadataRecord::timestamp.name).`is`(it) }
            return criteria
        }
    }
}
