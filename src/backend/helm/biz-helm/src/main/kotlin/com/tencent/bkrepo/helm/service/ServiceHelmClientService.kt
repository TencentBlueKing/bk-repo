package com.tencent.bkrepo.helm.service

interface ServiceHelmClientService {
    fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    )
}
