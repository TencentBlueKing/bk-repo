package com.tencent.bkrepo.auth.pojo.permission

data class CheckPermissionContext(
    var userId: String,
    var roles: List<String>,
    var resourceType: String,
    var action: String,
    var projectId: String,
    var repoName: String? = null,
    var path: String? = null,
)