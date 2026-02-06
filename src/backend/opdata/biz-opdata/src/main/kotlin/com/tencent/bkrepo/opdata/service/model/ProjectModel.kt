package com.tencent.bkrepo.opdata.service.model

import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT
import com.tencent.bkrepo.opdata.pojo.enums.ProjectType
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service

@Service
class ProjectModel @Autowired constructor(
    private var mongoTemplate: MongoTemplate
) {

    fun getProjectNum(projectType: ProjectType): Long {
        val query = when (projectType) {
            ProjectType.ALL -> Query()
            ProjectType.BLUEKING -> {
                val criteriaList = mutableListOf<String>()
                criteriaList.addAll(ProjectType.GIT.prefix!!)
                criteriaList.addAll(ProjectType.CODECC.prefix!!)
                Query(
                    Criteria().andOperator(buildCriteria(criteriaList))
                )
            }
            ProjectType.CODECC -> {
                Query(
                    Criteria().orOperator(
                        buildCriteria(ProjectType.CODECC.prefix!!.toMutableList(), false)
                    )
                )
            }
            ProjectType.GIT -> {
                Query(
                    Criteria().orOperator(
                        buildCriteria(ProjectType.GIT.prefix!!.toMutableList(), false)
                    )
                )
            }
        }
        return mongoTemplate.count(query, OPDATA_PROJECT)
    }

    fun getProjectList(): List<ProjectInfo> {
        return mongoTemplate.findAll(ProjectInfo::class.java, OPDATA_PROJECT)
    }

    private fun buildCriteria(prefixList: MutableList<String>, notFlag: Boolean = true): List<Criteria> {
        return prefixList.map {
            if (notFlag) {
                where(ProjectInfo::name).not().regex(it)
            } else {
                where(ProjectInfo::name).regex(it)
            }
        }
    }
}