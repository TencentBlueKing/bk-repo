package com.tencent.bkrepo.replication.pojo.request

data class PackageVersionDeleteRequest(
    val projectId: String,
    val repoName: String,
    val packageKey: String,
    val versionName: String,
    val deletedDate: String,
    val source: String
)
