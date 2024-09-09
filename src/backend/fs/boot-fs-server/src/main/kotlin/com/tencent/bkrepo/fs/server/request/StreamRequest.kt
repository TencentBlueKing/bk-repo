package com.tencent.bkrepo.fs.server.request

import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.queryParamOrNull

class StreamRequest(val request: ServerRequest) : NodeRequest(request) {
    val size = request.queryParamOrNull("size")?.toLong()
        ?: throw ParameterInvalidException("required size parameter.")
    val overwrite = request.headers().header("X-BKREPO-OVERWRITE").firstOrNull()?.toBoolean() ?: false
    val expires = request.headers().header("X-BKREPO-EXPIRES").firstOrNull()?.toLong() ?: 0
}
