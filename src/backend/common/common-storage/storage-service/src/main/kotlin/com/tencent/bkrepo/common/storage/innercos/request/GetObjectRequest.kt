package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.storage.innercos.http.Headers.Companion.RANGE
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod

data class GetObjectRequest(
    val key: String,
    val rangeStart: Long? = null,
    val rangeEnd: Long? = null
) : CosRequest(HttpMethod.GET, key) {

    init {
        if (rangeStart != null || rangeEnd != null) {
            val rangeStartStr = rangeStart?.toString().orEmpty()
            val rangeEndStr = rangeEnd?.toString().orEmpty()
            headers[RANGE] = "bytes=$rangeStartStr-$rangeEndStr"
        }
    }
}
