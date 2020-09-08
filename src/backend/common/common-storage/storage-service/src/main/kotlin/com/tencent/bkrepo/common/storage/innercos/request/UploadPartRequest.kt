package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.artifact.stream.BoundedInputStream
import com.tencent.bkrepo.common.storage.innercos.PARAMETER_PART_NUMBER
import com.tencent.bkrepo.common.storage.innercos.PARAMETER_UPLOAD_ID
import com.tencent.bkrepo.common.storage.innercos.http.Headers.Companion.CONTENT_LENGTH
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.innercos.http.InputStreamRequestBody
import okhttp3.RequestBody
import java.io.File

data class UploadPartRequest(
    val key: String,
    val uploadId: String,
    val partNumber: Int,
    val partSize: Long,
    val file: File,
    val fileOffset: Long
) : CosRequest(HttpMethod.PUT, key) {

    init {
        parameters[PARAMETER_UPLOAD_ID] = uploadId
        parameters[PARAMETER_PART_NUMBER] = partNumber.toString()
        headers[CONTENT_LENGTH] = partSize.toString()
    }

    override fun buildRequestBody(): RequestBody {
        val inputStream = BoundedInputStream(file.inputStream().apply { skip(fileOffset) }, partSize)
        return InputStreamRequestBody(inputStream)
    }
}
