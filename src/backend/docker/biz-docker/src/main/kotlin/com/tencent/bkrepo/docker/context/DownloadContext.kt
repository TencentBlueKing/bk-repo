package com.tencent.bkrepo.docker.context

import com.google.common.collect.Maps
import java.io.InputStream
import org.springframework.http.HttpHeaders

class DownloadContext(projectId: String, repoName: String, path: String) {
    private var skipStatsUpdate = false
    private val requestHeaders = Maps.newHashMap<String, String>()

    var name: String = ""
    var content: InputStream? = null
    var contentLength: Long = 0
    var sha256: String = ""
    var md5: String = ""
    var projectId: String = ""
    var repoName: String = ""
    var fullPath: String = ""

    init {
        this.projectId = projectId
        this.repoName = repoName
        this.fullPath = path
    }

    fun path(name: String): DownloadContext {
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

    fun header(key: String?, value: String?): DownloadContext {
        if (key != null && value != null) {
            this.requestHeaders[key] = value
        }

        return this
    }

    fun headers(httpHeaders: HttpHeaders?): DownloadContext {
        if (!httpHeaders.isNullOrEmpty()) {
            val multiMap = httpHeaders.toMap()
            val headerKeys = multiMap.keys.iterator()

            while (headerKeys.hasNext()) {
                val key = headerKeys.next() as String
                val requestHeader = multiMap[key] as kotlin.collections.List<*>
                if (requestHeader.size >= 1) {
                    this.header(key, requestHeader[0] as String)
                }
            }
        }

        return this
    }

    fun isSkipStatsUpdate(): Boolean {
        return this.skipStatsUpdate
    }

    fun setSkipStatsUpdate(skipStatsUpdate: Boolean) {
        this.skipStatsUpdate = skipStatsUpdate
    }
}
