package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service

@Service
class ProjectModel @Autowired constructor(
    private var mongoTemplate: MongoTemplate
) {

    fun getProjectNum(): Long {
        val results = mongoTemplate.findAll(MutableMap::class.java, "project")
        return results.size.toLong()
    }

    fun getProjectList(): List<ProjectInfo> {
        val results = mongoTemplate.findAll(ProjectInfo::class.java, "project")
        return results
    }
}
