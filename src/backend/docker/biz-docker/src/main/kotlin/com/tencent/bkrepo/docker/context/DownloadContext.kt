package com.tencent.bkrepo.docker.context

import com.google.common.collect.Maps
import org.springframework.http.HttpHeaders
import java.io.InputStream

class DownloadContext(projectId: String, repoName: String, path: String) {
    val FORCE_GET_STREAM_HEADER = "artifactory.disableRedirect"
    private var skipStatsUpdate = false
    private val requestHeaders = Maps.newHashMap<String, String>()

    var name: String = ""
    var content: InputStream? = null
    var contentLength: Long = 0
    var sha1: String = ""
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

    fun header(key: String, value: String): DownloadContext {
        if (key != null && value != null) {
            this.requestHeaders[key] = value
        }

        return this
    }

    fun getPath(): String {
        return this.name
    }

    fun getRequestHeaders(): kotlin.collections.Map<String, String> {
        return this.requestHeaders
    }

    fun headers(httpHeaders: HttpHeaders?): DownloadContext {
        if (!httpHeaders.isNullOrEmpty()) {
            val multiMap = httpHeaders.toMap()
            val var3 = multiMap.keys.iterator()

            while (var3.hasNext()) {
                val key = var3.next() as String
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

    override fun equals(o: Any?): Boolean {
        if (o === this) {
            return true
        } else if (o !is DownloadContext) {
            return false
        } else {
            val other = o as DownloadContext?
            if (!other!!.canEqual(this)) {
                return false
            } else {
                run loop@{
                    val `this$path` = this.getPath()
                    val `other$path` = other.getPath()
                    if (`this$path` == null) {
                        if (`other$path` == null) {
                            // TODO : break
                            // break@loop
                        }
                    } else if (`this$path` == `other$path`) {
                        // TODO: break
                        // break@loop
                    }

                    return false
                }

                if (this.isSkipStatsUpdate() != other.isSkipStatsUpdate()) {
                    return false
                } else {
                    val `this$requestHeaders` = this.getRequestHeaders()
                    val `other$requestHeaders` = other.getRequestHeaders()
                    if (`this$requestHeaders` == null) {
                        if (`other$requestHeaders` != null) {
                            return false
                        }
                    } else if (`this$requestHeaders` != `other$requestHeaders`) {
                        return false
                    }

                    return true
                }
            }
        }
    }

    protected fun canEqual(other: Any): Boolean {
        return other is DownloadContext
    }
}
