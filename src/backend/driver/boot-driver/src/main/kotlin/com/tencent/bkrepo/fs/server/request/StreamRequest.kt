package com.tencent.bkrepo.fs.server.request

import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.queryParamOrNull

class StreamRequest(val request: ServerRequest) : NodeRequest(request) {
    val size = try {
        request.queryParamOrNull("size")?.toLong() ?: throw ParameterInvalidException("size")
    } catch (e: NumberFormatException) {
        logger.info("invalid size parameter: ${request.queryParamOrNull("size")}")
        throw ParameterInvalidException("size")
    }

    val overwrite = request.headers().header("X-BKREPO-OVERWRITE").firstOrNull()?.toBoolean() ?: false
    val expires = request.headers().header("X-BKREPO-EXPIRES").firstOrNull()?.toLong() ?: 0

    companion object {
        private val logger = LoggerFactory.getLogger(StreamRequest::class.java)
    }
}
