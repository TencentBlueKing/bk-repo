package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.http.Headers
import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.CosObject
import okhttp3.Response

class GetObjectResponseHandler : HttpResponseHandler<CosObject>() {
    override fun handle(response: Response): CosObject {
        val eTag = response.header(Headers.ETAG)!!.trim('"')
        val inputStream = response.body()?.byteStream()
        return CosObject(eTag, inputStream)
    }

    override fun keepConnection() = true

    override fun handle404(): CosObject {
        return CosObject(null, null)
    }
}
