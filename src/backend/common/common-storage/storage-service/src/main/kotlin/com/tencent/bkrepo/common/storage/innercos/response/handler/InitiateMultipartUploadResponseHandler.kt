package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.RESPONSE_UPLOAD_ID
import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import okhttp3.Response

class InitiateMultipartUploadResponseHandler : HttpResponseHandler<String>() {
    override fun handle(response: Response): String {
        return readXmlValue(response)[RESPONSE_UPLOAD_ID] as String
    }
}
