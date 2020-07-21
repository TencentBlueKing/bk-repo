package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import okhttp3.Response

class VoidResponseHandler : HttpResponseHandler<Unit>() {
    override fun handle(response: Response) {
    }
}
