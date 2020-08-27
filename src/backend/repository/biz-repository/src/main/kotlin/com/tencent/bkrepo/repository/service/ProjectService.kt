package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo

/**
 * 项目服务
 */
interface ProjectService {
    fun query(name: String): ProjectInfo?
    fun list(): List<ProjectInfo>
    fun exist(name: String): Boolean
    fun create(request: ProjectCreateRequest): ProjectInfo
    fun checkProject(name: String)
}
