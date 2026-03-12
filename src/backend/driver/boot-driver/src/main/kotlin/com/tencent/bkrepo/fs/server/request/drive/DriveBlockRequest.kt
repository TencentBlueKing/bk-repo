package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import org.springframework.web.reactive.function.server.ServerRequest

open class DriveBlockRequest(request: ServerRequest) : DriveNodeRequest(request) {
    open val ino: Long = try {
        request.pathVariable("ino").toLong()
    } catch (_: NumberFormatException) {
        throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "ino")
    }

    override fun toString(): String {
        return "$projectId/$repoName/$ino"
    }
}
