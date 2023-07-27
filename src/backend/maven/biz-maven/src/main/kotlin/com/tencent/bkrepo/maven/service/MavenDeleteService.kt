package com.tencent.bkrepo.maven.service

interface MavenDeleteService {
    fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    ): Boolean
}
