package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration

data class DriveRepoCreateRequest(
    val projectId: String,
    val name: String,
    val storageCredentialsKey: String? = null,
    val quota: Long? = null,
    val description: String? = null,
    val configuration: RepositoryConfiguration? = null,
)

data class UserDriveRepoCreateRequest(
    val storageCredentialsKey: String? = null,
    val quota: Long? = null,
    val description: String? = null,
    val configuration: RepositoryConfiguration? = null,
) {
    fun toReq(projectId: String, repoName: String) = DriveRepoCreateRequest(
        projectId = projectId,
        name = repoName,
        storageCredentialsKey = storageCredentialsKey,
        quota = quota,
        description = description,
        configuration = configuration
    )
}
