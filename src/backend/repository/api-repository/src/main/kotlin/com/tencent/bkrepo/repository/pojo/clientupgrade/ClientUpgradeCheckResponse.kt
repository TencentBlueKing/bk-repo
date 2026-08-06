package com.tencent.bkrepo.repository.pojo.clientupgrade

data class ClientUpgradeCheckResponse(
    val repositoryManaged: Boolean,
    val needUpgrade: Boolean,
    val forceUpgrade: Boolean,
    val latestVersion: String?,
    val downloadUrl: String?,
    val releaseNotes: String?,
)
