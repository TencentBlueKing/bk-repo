package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.storage.innercos.PARAMETER_UPLOAD_ID
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod

data class AbortMultipartUploadRequest(
    val key: String,
    val uploadId: String
) : CosRequest(HttpMethod.DELETE, key) {

    init {
        parameters[PARAMETER_UPLOAD_ID] = uploadId
    }
}
