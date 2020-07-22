package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.RESPONSE_LAST_MODIFIED
import com.tencent.bkrepo.common.storage.innercos.http.Headers.Companion.ETAG
import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.CopyObjectResponse
import okhttp3.Response

class CopyObjectResponseHandler : HttpResponseHandler<CopyObjectResponse>() {
    override fun handle(response: Response): CopyObjectResponse {
        val result = readXmlValue(response)
        val eTag = (result[ETAG] as String).trim('"')
        val lastModified = result[RESPONSE_LAST_MODIFIED] as String
        return CopyObjectResponse(eTag, lastModified)
    }
}
