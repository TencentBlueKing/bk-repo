package com.tencent.bkrepo.docker.repomd

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Maps
import com.google.common.collect.SetMultimap
import java.io.InputStream

class WriteContext {
    var path: String = ""
    var content: InputStream? = null
    var contentLength: Long = 0
    var sha1: String = ""
    var sha256: String = ""
    var md5: String = ""
    var projectId: String = ""
    var repoName: String = ""
    var userId: String = "bk_admin"
    private val requestHeaders = Maps.newHashMap<String, String>()
    var attributes: SetMultimap<String, String> = LinkedHashMultimap.create()


    constructor(projectId: String, repoName: String,path: String) {
        this.projectId = projectId
        this.repoName = repoName
        this.path = path
    }

    fun path(path: String): WriteContext {
        this.path = path
        return this
    }

    fun content(content: InputStream): WriteContext {
        this.content = content
        return this
    }

    fun contentLength(contentLength: Long): WriteContext {
        this.contentLength = contentLength
        return this
    }

    fun sha1(sha1: String): WriteContext {
        this.sha1 = sha1
        return this
    }

    fun sha256(sha256: String): WriteContext {
        this.sha256 = sha256
        return this
    }

    fun md5(md5: String): WriteContext {
        this.md5 = md5
        return this
    }

    fun projectId(projectId: String): WriteContext {
        this.projectId = projectId
        return this
    }

    fun repoName(repoName: String): WriteContext {
        this.repoName = repoName
        return this
    }

    fun use(repoName: String): WriteContext {
        this.repoName = repoName
        return this
    }

    fun header(key: String?, value: String?): WriteContext {
        if (key != null && value != null) {
            this.requestHeaders[key] = value
        }

        return this
    }

    fun attributes(attributes: SetMultimap<String, String>): WriteContext {
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
