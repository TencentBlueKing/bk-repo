package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod

data class DeleteObjectRequest(
    val key: String
) : CosRequest(HttpMethod.DELETE, key)
