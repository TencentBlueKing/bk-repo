package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service

@Service
class ProjectModel @Autowired constructor(
    private var mongoTemplate: MongoTemplate
) {

    fun getProjectNum(): Long {
        val results = mongoTemplate.findAll(MutableMap::class.java, OPDATA_PROJECT)
        return results.size.toLong()
    }

    fun getProjectList(): List<ProjectInfo> {
        return mongoTemplate.findAll(ProjectInfo::class.java, OPDATA_PROJECT)
    }
}
