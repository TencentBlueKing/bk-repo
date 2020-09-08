package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.storage.innercos.PARAMETER_UPLOADS
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import okhttp3.RequestBody

data class InitiateMultipartUploadRequest(
    val key: String
) : CosRequest(HttpMethod.POST, key) {

    init {
        parameters[PARAMETER_UPLOADS] = null
    }
    override fun buildRequestBody(): RequestBody {
        return StringPool.EMPTY.toRequestBody()
    }
}
