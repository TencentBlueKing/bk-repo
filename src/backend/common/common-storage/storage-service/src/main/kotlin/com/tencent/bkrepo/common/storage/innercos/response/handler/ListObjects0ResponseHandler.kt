package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.ListObjectsResponse
import okhttp3.Response

class ListObjects0ResponseHandler : HttpResponseHandler<ListObjectsResponse>() {
    override fun handle(response: Response): ListObjectsResponse {
        return readXmlValue(response)
    }
}
