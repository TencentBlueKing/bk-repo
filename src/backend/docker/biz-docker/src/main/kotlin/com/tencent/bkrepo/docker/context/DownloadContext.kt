package com.tencent.bkrepo.docker.context

import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY

/**
 * docker registry download context
*/
data class DownloadContext(val requestContext: RequestContext) {

    var context: RequestContext = requestContext
    var length: Long = 0L
    var sha256: String = EMPTY

    fun sha256(sha256: String): DownloadContext {
        this.sha256 = sha256
        return this
    }

    fun length(length: Long): DownloadContext {
        this.length = length
        return this
    }
}
