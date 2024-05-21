package com.tencent.bkrepo.auth.dao

import com.tencent.bkrepo.auth.model.TPersonalPath
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository


@Repository
class PersonalPathDao : SimpleMongoDao<TPersonalPath>() {

    fun findOneByProjectAndRepo(userId: String, projectId: String, repoName: String): TPersonalPath? {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPersonalPath::userId.name).`is`(userId),
                Criteria.where(TPersonalPath::projectId.name).`is`(projectId),
                Criteria.where(TPersonalPath::repoName.name).`is`(repoName),
            )
        )
        return this.findOne(query)
    }

    fun listByProjectAndRepoAndExcludeUser(userId: String, projectId: String, repoName: String): List<TPersonalPath> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPersonalPath::userId.name).ne(userId),
                Criteria.where(TPersonalPath::projectId.name).`is`(projectId),
                Criteria.where(TPersonalPath::repoName.name).`is`(repoName),
            )
        )
        return this.find(query)
    }

}