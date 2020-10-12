package com.tencent.bkrepo.dockerapi.service

import com.tencent.bkrepo.dockerapi.pojo.ImageAccount
import com.tencent.bkrepo.dockerapi.pojo.Repository

interface RepoService {
    fun createRepo(projectId: String): Repository
    fun createAccount(projectId: String): ImageAccount
}
