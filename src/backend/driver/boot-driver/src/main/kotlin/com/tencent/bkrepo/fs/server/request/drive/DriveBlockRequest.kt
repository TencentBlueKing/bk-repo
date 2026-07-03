package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import org.springframework.web.reactive.function.server.ServerRequest

open class DriveBlockRequest(
    projectId: String,
    repoName: String,
    open val ino: Long,
    open val snapSeq: Long? = null,
) : DriveNodeRequest(projectId, repoName) {

    constructor(request: ServerRequest) : this(
        projectId = request.pathVariable(PROJECT_ID),
        repoName = request.pathVariable(REPO_NAME),
        ino = parseIno(request),
        snapSeq = parseSnapSeq(request),
    )

    override fun toString(): String {
        return "$projectId/$repoName/$ino"
    }

    companion object {
        fun parseIno(request: ServerRequest): Long {
            return try {
                request.pathVariable("ino").toLong()
            } catch (_: NumberFormatException) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "ino")
            }
        }

        fun parseSnapSeq(request: ServerRequest): Long? {
            return request.queryParam("snapSeq").map {
                try {
                    it.toLong()
                } catch (_: NumberFormatException) {
                    throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "snapSeq")
                }
            }.orElse(null)
        }
    }
}
