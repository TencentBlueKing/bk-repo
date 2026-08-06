package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.fs.server.useRequestParam
import org.springframework.web.reactive.function.server.ServerRequest

/**
 * Drive 操作审计日志分页查询请求
 */
class DriveOpLogPageRequest(request: ServerRequest) {
    val projectId: String = request.pathVariable(PROJECT_ID)
    val repoName: String = request.pathVariable(REPO_NAME)
    var type: String? = null
    var operator: String? = null
    var startTime: String? = null
    var endTime: String? = null
    var pageNumber: Int = DEFAULT_PAGE_NUMBER
    var pageSize: Int = DEFAULT_PAGE_SIZE

    init {
        request.useRequestParam("type") { type = it }
        request.useRequestParam("operator") { operator = it }
        request.useRequestParam("startTime") { startTime = it }
        request.useRequestParam("endTime") { endTime = it }
        request.useRequestParam("pageNumber") { pageNumber = it.toInt() }
        request.useRequestParam("pageSize") { pageSize = it.toInt() }
    }
}
