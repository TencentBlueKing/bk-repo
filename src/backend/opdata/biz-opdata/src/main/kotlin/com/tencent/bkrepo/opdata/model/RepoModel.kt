package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

@Service
class RepoModel @Autowired constructor(
    private var mongoTemplate: MongoTemplate
) {

    fun getRepoListByProjectId(projectId: String): List<RepositoryInfo> {
        val query = Query(
            Criteria.where("projectId").`is`(projectId)
        )
        val results = mongoTemplate.find(query, RepositoryInfo::class.java, "repository")
        return results
    }
}
