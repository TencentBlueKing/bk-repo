package com.tencent.bkrepo.docker.context

/**
 * docker registry download context
 * @author: owenlxu
 * @date: 2019-12-01
*/
data class DownloadContext(val requestContext: RequestContext) {

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
