package com.tencent.bkrepo.common.storage.innercos.request

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.tencent.bkrepo.common.storage.innercos.PARAMETER_UPLOAD_ID
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import okhttp3.RequestBody

data class CompleteMultipartUploadRequest(
    val key: String,
    val uploadId: String,
    val partETagList: List<PartETag>
) : CosRequest(HttpMethod.POST, key) {

    init {
        parameters[PARAMETER_UPLOAD_ID] = uploadId
    }

    override fun buildRequestBody(): RequestBody {
        return xmlMapper.writeValueAsString(CompleteMultipartUpload(partETagList)).toRequestBody()
    }

    private data class CompleteMultipartUpload(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        @get:JacksonXmlProperty(localName = "Part")
        val partETagList: List<PartETag>
    )
}
