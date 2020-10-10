package com.tencent.bkrepo.dockeradapter.service

import com.tencent.bkrepo.dockeradapter.pojo.ImageAccount
import com.tencent.bkrepo.dockeradapter.pojo.Repository

interface RepoService {
    fun createRepo(projectId: String): Repository
    fun createAccount(projectId: String): ImageAccount
}
