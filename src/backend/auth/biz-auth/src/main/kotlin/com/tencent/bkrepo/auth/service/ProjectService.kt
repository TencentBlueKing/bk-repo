package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CreateProjectRequest
import com.tencent.bkrepo.auth.pojo.Project

interface ProjectService {
    fun createProject(request: CreateProjectRequest)
    fun deleteByName(name: String)
    fun listProject(): List<Project>
    fun getByName(name: String): Project?
}
