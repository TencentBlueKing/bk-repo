package com.tencent.bkrepo.repository.service.packages

interface PackageDownloadService {

    /**
     * 下载包版本
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageKey 包唯一标识
     * @param versionName 版本名称
     */
    fun downloadVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String,
        realIpAddress: String? = null
    )
}
