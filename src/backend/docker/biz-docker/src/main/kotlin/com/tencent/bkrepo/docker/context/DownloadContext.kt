package com.tencent.bkrepo.docker.context

import com.google.common.collect.Maps
import java.io.InputStream

class DownloadContext(projectId: String, repoName: String, path: String) {
    private val requestHeaders = Maps.newHashMap<String, String>()

    // name
    var name: String = ""
    var content: InputStream? = null
    var sha256: String = ""
    var projectId: String = ""
    var repoName: String = ""
    var length: Long = 0

    init {
        this.projectId = projectId
        this.repoName = repoName
        this.name = path
    }

    fun name(name: String): DownloadContext {
        this.name = name
        return this
    }

    fun projectId(projectId: String): DownloadContext {
        this.projectId = projectId
        return this
    }

    fun repoName(repoName: String): DownloadContext {
        this.repoName = repoName
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

    fun header(key: String?, value: String?): DownloadContext {
        if (key != null && value != null) {
            this.requestHeaders[key] = value
        }
        return this
    }
}
