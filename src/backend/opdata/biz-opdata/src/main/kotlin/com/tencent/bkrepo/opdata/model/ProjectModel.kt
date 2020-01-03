package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.repository.api.ProjectResource
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ProjectModel @Autowired constructor(
    private val projectResource: ProjectResource
) {

    fun getProjectNum(): Long {
        val result = projectResource.list()
        if (result.data == null) {
            return 0L
        }
        return result.data!!.size.toLong()
    }

    fun getProjectList(): List<ProjectInfo> {
        val result = projectResource.list().data ?: return emptyList()
        return result
    }
}
