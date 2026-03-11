package com.tencent.bkrepo.fs.server.request.drive

import org.springframework.web.reactive.function.server.ServerRequest

class DriveBlockRequest(request: ServerRequest) : DriveNodeRequest(request) {
    val ino: String = request.pathVariable("ino")
    val offset: Long = request.pathVariable("offset").toLong()

    override fun toString(): String {
        return "$projectId/$repoName/$ino/$offset"
    }
}
