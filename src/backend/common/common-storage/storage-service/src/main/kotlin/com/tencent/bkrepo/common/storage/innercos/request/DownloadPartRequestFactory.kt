package com.tencent.bkrepo.common.storage.innercos.request

import kotlin.math.min

class DownloadPartRequestFactory(
    private val key: String,
    private val optimalPartSize: Long,
    private val start: Long,
    private val end: Long
) {
    private var offset: Long = start
    private var remainingBytes: Long = end - start + 1
    fun hasMoreRequests(): Boolean {
        return remainingBytes > 0
    }

    fun nextDownloadPartRequest(): GetObjectRequest {
        val partSize = min(optimalPartSize, remainingBytes)
        val rangeStart = offset
        val rangeEnd = offset + partSize - 1
        val request = GetObjectRequest(key, rangeStart, rangeEnd)
        remainingBytes -= partSize
        offset = rangeEnd + 1
        return request
    }
}
