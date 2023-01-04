package com.tencent.bkrepo.fs.server.request

import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.queryParamOrNull

class ChangeAttributeRequest(request: ServerRequest) : NodeRequest(request) {
    val owner: String?
    val mode: Int?

    init {
        owner = request.queryParamOrNull("owner")
        mode = request.queryParamOrNull("mode")?.toIntOrNull()
    }
}
