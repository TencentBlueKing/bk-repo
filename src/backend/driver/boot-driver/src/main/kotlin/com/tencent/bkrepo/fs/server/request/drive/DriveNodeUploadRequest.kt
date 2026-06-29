package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.fs.server.constant.DriveUploadConstants.BKREPO_OVERWRITE
import com.tencent.bkrepo.fs.server.constant.DriveUploadConstants.BKREPO_SHA256
import com.tencent.bkrepo.fs.server.request.NodeRequest
import org.springframework.web.reactive.function.server.ServerRequest

class DriveNodeUploadRequest : NodeRequest {
    val expectedSha256: String?
    val overwrite: Boolean

    constructor(request: ServerRequest) : super(request) {
        expectedSha256 = request.headers().firstHeader(BKREPO_SHA256)
        overwrite = request.headers().header(BKREPO_OVERWRITE).firstOrNull()?.toBoolean() ?: false
    }

    constructor(
        projectId: String,
        repoName: String,
        fullPath: String,
        expectedSha256: String? = null,
        overwrite: Boolean = false,
    ) : super(projectId, repoName, fullPath) {
        this.expectedSha256 = expectedSha256
        this.overwrite = overwrite
    }
}
