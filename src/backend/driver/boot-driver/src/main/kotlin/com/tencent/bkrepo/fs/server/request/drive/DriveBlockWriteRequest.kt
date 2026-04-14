package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import org.springframework.web.reactive.function.server.ServerRequest

class DriveBlockWriteRequest(request: ServerRequest) : DriveBlockRequest(request) {
    val offset: Long = try {
        request.pathVariable("offset").toLong()
    } catch (_: NumberFormatException) {
        throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "offset")
    }

    override fun toString(): String {
        return "${super.toString()}/$offset"
    }
}
