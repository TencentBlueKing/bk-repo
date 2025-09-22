package com.tencent.bkrepo.replication.pojo.request

data class PackageDeleteRequest(
    val projectId: String,
    val repoName: String,
    val packageKey: String,
    val deletedDate: String,
    val source: String
)
