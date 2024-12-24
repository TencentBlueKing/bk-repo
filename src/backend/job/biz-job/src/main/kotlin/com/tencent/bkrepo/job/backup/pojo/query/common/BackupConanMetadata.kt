package com.tencent.bkrepo.job.backup.pojo.query.common

data class BackupConanMetadata(
    var id: String?,
    val projectId: String,
    val repoName: String,
    val user: String,
    val name: String,
    val version: String,
    val channel: String,
    val recipe: String
)