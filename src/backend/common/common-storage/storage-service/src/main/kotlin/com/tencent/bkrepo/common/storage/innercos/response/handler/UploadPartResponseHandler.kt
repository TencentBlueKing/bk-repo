package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.http.Headers
import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.UploadPartResponse
import okhttp3.Response

class UploadPartResponseHandler : HttpResponseHandler<UploadPartResponse>() {
    override fun handle(response: Response): UploadPartResponse {
        val eTag = response.header(Headers.ETAG)!!.trim('"')
        return UploadPartResponse(eTag)
    }
}
