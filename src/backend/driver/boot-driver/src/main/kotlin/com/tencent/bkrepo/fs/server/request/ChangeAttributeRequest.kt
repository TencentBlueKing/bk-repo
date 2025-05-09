package com.tencent.bkrepo.fs.server.request

import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.queryParamOrNull

class ChangeAttributeRequest(request: ServerRequest) : NodeRequest(request) {
    val uid: String?
    val gid: String?

    init {
        uid = request.queryParamOrNull("uid")
        gid = request.queryParamOrNull("gid")
    }
}
