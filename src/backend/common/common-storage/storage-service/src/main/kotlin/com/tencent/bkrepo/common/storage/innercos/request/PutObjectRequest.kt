package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.storage.innercos.http.Headers.Companion.CONTENT_LENGTH
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.innercos.http.InputStreamRequestBody
import okhttp3.RequestBody
import java.io.InputStream

data class PutObjectRequest(
    val key: String,
    val inputStream: InputStream,
    val length: Long
) : CosRequest(HttpMethod.PUT, key) {

    init {
        headers[CONTENT_LENGTH] = length.toString()
    }

    override fun buildRequestBody(): RequestBody {
        return InputStreamRequestBody(inputStream)
    }
}
