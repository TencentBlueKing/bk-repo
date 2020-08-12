package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.http.Headers
import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.PutObjectResponse
import okhttp3.Response

class PutObjectResponseHandler : HttpResponseHandler<PutObjectResponse>() {
    override fun handle(response: Response): PutObjectResponse {
        val eTag = response.header(Headers.ETAG)!!.trim('"')
        return PutObjectResponse(eTag)
    }
}
