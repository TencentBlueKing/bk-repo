package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.http.Headers.Companion.ETAG
import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.PutObjectResponse
import okhttp3.Response

class CompleteMultipartUploadResponseHandler : HttpResponseHandler<PutObjectResponse>() {
    override fun handle(response: Response): PutObjectResponse {
        return PutObjectResponse(readXmlValue(response)[ETAG] as String)
    }
}
