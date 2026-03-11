package com.tencent.bkrepo.fs.server.request.drive

import org.springframework.web.reactive.function.server.ServerRequest

class DriveBlockWriteRequest(request: ServerRequest) : DriveBlockRequest(request) {
    val offset: Long = request.pathVariable("offset").toLong()

    override fun toString(): String {
        return "${super.toString()}/$offset"
    }
}
