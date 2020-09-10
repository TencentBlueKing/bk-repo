package com.tencent.bkrepo.docker.context

import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY

/**
 * docker registry download context
*/
data class DeleteContext(val requestContext: RequestContext) {

    var context: RequestContext = requestContext
    var length: Long = 0L
    var sha256: String = EMPTY

    fun sha256(sha256: String): DeleteContext {
        this.sha256 = sha256
        return this
    }

    fun length(length: Long): DeleteContext {
        this.length = length
        return this
    }
}
