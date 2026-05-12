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

    /**
     * 可选的快照序列号，用于读取指定快照的数据
     * 不传则读取当前最新数据
     */
    open val snapSeq: Long? = request.queryParam("snapSeq").map {
        try {
            it.toLong()
        } catch (_: NumberFormatException) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "snapSeq")
        }
    }.orElse(null)

    override fun toString(): String {
        return "$projectId/$repoName/$ino"
    }
}
