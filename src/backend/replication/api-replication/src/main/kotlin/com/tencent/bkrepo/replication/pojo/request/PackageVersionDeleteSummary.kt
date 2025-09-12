package com.tencent.bkrepo.replication.pojo.request

data class PackageVersionDeleteSummary(
    val projectId: String,
    val repoName: String,
    val packageKey: String,
    val packageName: String,
    val versionName: String? = null,
    val deletedDate: String,
)
