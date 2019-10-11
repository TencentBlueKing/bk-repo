package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.CreateProjectRequest
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.Project
import com.tencent.bkrepo.auth.pojo.User

interface ProjectService {
    fun createProject(request: CreateProjectRequest)
    fun deleteByName(name: String)
    fun listProject(): List<Project>
}
