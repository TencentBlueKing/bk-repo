package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT_ID
import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT_NAME
import com.tencent.bkrepo.opdata.constant.OPDATA_REPOSITORY
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
            Criteria.where(OPDATA_PROJECT_ID).`is`(projectId)
        )
        val data = mutableListOf<String>()
        val results = mongoTemplate.find(query, MutableMap::class.java, OPDATA_REPOSITORY)
        results.forEach {
            val repoName = it[OPDATA_PROJECT_NAME] as String
            data.add(repoName)
        }
        return data
    }
}
