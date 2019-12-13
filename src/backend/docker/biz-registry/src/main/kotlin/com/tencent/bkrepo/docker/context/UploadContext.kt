package com.tencent.bkrepo.docker.context

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Maps
import com.google.common.collect.SetMultimap
import java.io.InputStream

class UploadContext {
    var path: String = ""
    var content: InputStream? = null
    var contentLength: Long = 0
    var sha1: String = ""
    var sha256: String = ""
    var md5: String = ""
    var projectId: String = ""
    var repoName: String = ""
    private val requestHeaders = Maps.newHashMap<String, String>()
    var attributes: SetMultimap<String, String> = LinkedHashMultimap.create()

    constructor(projectId:String,repoName: String,path: String) {
        this.projectId = projectId
        this.repoName = repoName
        this.path = path
    }

    fun path(path: String): UploadContext {
        this.path = path
        return this
    }

    fun content(content: InputStream): UploadContext {
        this.content = content
        return this
    }

    fun contentLength(contentLength: Long): UploadContext {
        this.contentLength = contentLength
        return this
    }

    fun sha1(sha1: String): UploadContext {
        this.sha1 = sha1
        return this
    }

    fun sha256(sha256: String): UploadContext {
        this.sha256 = sha256
        return this
    }

    fun md5(md5: String): UploadContext {
        this.md5 = md5
        return this
    }

    fun projectId(projectId: String): UploadContext {
        this.projectId = projectId
        return this
    }

    fun repoName(repoName: String): UploadContext {
        this.repoName = repoName
        return this
    }

    fun use(repoName: String): UploadContext {
        this.repoName = repoName
        return this
    }

    fun header(key: String?, value: String?): UploadContext {
        if (key != null && value != null) {
            this.requestHeaders[key] = value
        }

        return this
    }

    fun attributes(attributes: SetMultimap<String, String>): UploadContext {
        if (!attributes.isEmpty) {
            this.attributes = attributes
        }

        return this
    }

    fun getRequestHeaders(): Map<String, String> {
        return this.requestHeaders
    }

    fun addRequestHeaders(requestHeaders: Map<String, String>) {
        this.requestHeaders.putAll(requestHeaders)
    }
}
