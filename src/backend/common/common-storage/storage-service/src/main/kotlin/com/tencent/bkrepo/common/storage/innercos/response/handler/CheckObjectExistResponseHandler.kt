package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import okhttp3.Response

class CheckObjectExistResponseHandler : HttpResponseHandler<Boolean>() {
    override fun handle(response: Response): Boolean {
        return true
    }

    override fun handle404(): Boolean {
        return false
    }
}
