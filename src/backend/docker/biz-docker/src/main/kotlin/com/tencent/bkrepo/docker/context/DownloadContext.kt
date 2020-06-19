package com.tencent.bkrepo.docker.context

class DownloadContext(requestContext: RequestContext) {

    var context: RequestContext = requestContext
    var length: Long = 0L
    var sha256: String = ""

    fun sha256(sha256: String): DownloadContext {
        this.sha256 = sha256
        return this
    }

    fun length(length: Long): DownloadContext {
        this.length = length
        return this
    }
}
