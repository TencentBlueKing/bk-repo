package com.tencent.bkrepo.common.storage.innercos.request

import java.io.File
import kotlin.math.ceil
import kotlin.math.min

class UploadPartRequestFactory(
    private val key: String,
    private val uploadId: String,
    private val optimalPartSize: Long,
    private val file: File,
    length: Long
) {
    private var partNumber = 1
    private var offset: Long = 0
    private var remainingBytes: Long = length
    private val totalNumberOfParts: Int

    init {
        totalNumberOfParts = ceil(remainingBytes.toDouble() / optimalPartSize).toInt()
    }

    fun hasMoreRequests(): Boolean {
        return remainingBytes > 0
    }

    fun nextUploadPartRequest(): UploadPartRequest {
        val partSize = min(optimalPartSize, remainingBytes)
        val cosRequest = UploadPartRequest(key, uploadId, partNumber, partSize, file, offset)
        offset += partSize
        remainingBytes -= partSize
        partNumber += 1
        return cosRequest
    }
}
