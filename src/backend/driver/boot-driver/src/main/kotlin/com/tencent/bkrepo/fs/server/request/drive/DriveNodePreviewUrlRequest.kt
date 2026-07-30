package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.queryParamOrNull

/**
 * Drive 节点预览 URL 请求
 *
 * [type] 为预览调用方类型（字符串），与 NodeRequest 中表示文件系统节点类型的整型 type 无关。
 */
class DriveNodePreviewUrlRequest(request: ServerRequest) : DriveNodeRequest(
    projectId = request.pathVariable(PROJECT_ID),
    repoName = request.pathVariable(REPO_NAME),
) {
    val ino: Long = request.pathVariable("ino").toLong()
    val type: String? = request.queryParamOrNull("type")
}
