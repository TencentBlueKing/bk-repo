package com.tencent.bkrepo.auth.pojo.permission

data class PersonalPathInfo(
    val userId: String,
    val projectId: String,
    val repoName: String,
    val fullPath: String,
)
