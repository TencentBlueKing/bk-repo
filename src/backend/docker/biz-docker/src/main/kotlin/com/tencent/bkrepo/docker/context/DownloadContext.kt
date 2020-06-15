package com.tencent.bkrepo.docker.context

import java.io.InputStream

class DownloadContext(projectId: String, repoName: String, name: String) {
    // name
    var projectId: String = ""
    var repoName: String = ""
    var name: String = ""

    var length: Long = 0L
    var content: InputStream? = null
    var sha256: String = ""

    init {
        this.projectId = projectId
        this.repoName = repoName
        this.name = name
    }

    fun projectId(projectId: String): DownloadContext {
        this.projectId = projectId
        return this
    }

    fun repoName(repoName: String): DownloadContext {
        this.repoName = repoName
        return this
    }

    fun name(name: String): DownloadContext {
        this.name = name
        return this
    }

    fun sha256(sha256: String): DownloadContext {
        this.sha256 = sha256
        return this
    }

    fun length(length: Long): DownloadContext {
        this.length = length
        return this
    }
}
