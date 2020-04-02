package com.tencent.bkrepo.opdata.model

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

@Service
class RepoModel @Autowired constructor(
    private var mongoTemplate: MongoTemplate
) {

    fun getRepoListByProjectId(projectId: String): List<String> {
        val query = Query(
            Criteria.where("projectId").`is`(projectId)
        )
        var data = mutableListOf<String>()
        val results = mongoTemplate.find(query, MutableMap::class.java, "repository")
        results.forEach {
            val repoName = it.get("name") as String
            data.add(repoName)
        }
        return data
    }
}
