package com.tencent.bkrepo.common.storage.innercos.http

interface Headers {
    companion object {
        const val HOST = "Host"
        const val CONTENT_LENGTH = "Content-Length"
        const val RANGE = "Range"
        const val ETAG = "ETag"
        const val AUTHORIZATION = "Authorization"
    }
}
