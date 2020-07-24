package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod

data class CheckObjectExistRequest(
    val key: String
) : CosRequest(HttpMethod.HEAD, key)
