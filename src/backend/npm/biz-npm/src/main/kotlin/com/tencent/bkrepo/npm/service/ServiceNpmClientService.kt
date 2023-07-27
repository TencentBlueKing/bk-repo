package com.tencent.bkrepo.npm.service

interface ServiceNpmClientService {
    fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    )
}
