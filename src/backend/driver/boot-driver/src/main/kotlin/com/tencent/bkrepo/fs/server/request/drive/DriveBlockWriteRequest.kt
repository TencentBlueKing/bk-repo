package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import org.springframework.web.reactive.function.server.ServerRequest

class DriveBlockWriteRequest(
    projectId: String,
    repoName: String,
    ino: Long,
    val offset: Long,
) : DriveBlockRequest(projectId, repoName, ino) {

    constructor(request: ServerRequest) : this(
        projectId = request.pathVariable(PROJECT_ID),
        repoName = request.pathVariable(REPO_NAME),
        ino = DriveBlockRequest.parseIno(request),
        offset = parseOffset(request),
    )

    override fun toString(): String {
        return "${super.toString()}/$offset"
    }

    companion object {
        private fun parseOffset(request: ServerRequest): Long {
            return try {
                request.pathVariable("offset").toLong()
            } catch (_: NumberFormatException) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "offset")
            }
        }
    }
}
