package com.tencent.bkrepo.fs.server.request.drive

import org.springframework.web.reactive.function.server.ServerRequest

open class DriveBlockRequest(request: ServerRequest) : DriveNodeRequest(request) {
    open val ino: String = request.pathVariable("ino")

    override fun toString(): String {
        return "$projectId/$repoName/$ino"
    }
}
