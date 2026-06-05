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

    /**
     * 列出该项目下所有 personal_path 记录（用于"项目维度残留聚合"扫描）。
     */
    fun listByProject(projectId: String): List<TPersonalPath> {
        val query = Query(Criteria.where(TPersonalPath::projectId.name).`is`(projectId))
        return this.find(query)
    }

    /**
     * 删除指定项目下、指定用户的所有 personal_path 记录。
     * @return 删除条数
     */
    fun deleteByProjectAndUser(projectId: String, userId: String): Long {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPersonalPath::projectId.name).`is`(projectId),
                Criteria.where(TPersonalPath::userId.name).`is`(userId)
            )
        )
        return this.remove(query).deletedCount
    }

}