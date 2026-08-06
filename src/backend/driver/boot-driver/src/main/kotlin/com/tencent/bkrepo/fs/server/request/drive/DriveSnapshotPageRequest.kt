package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.fs.server.useRequestParam
import org.springframework.web.reactive.function.server.ServerRequest

class DriveSnapshotPageRequest(request: ServerRequest) : DriveSnapshotRequest(request) {
    var pageSize: Int = DEFAULT_PAGE_SIZE
    var pageNum: Int = DEFAULT_PAGE_NUMBER

    init {
        request.useRequestParam("pageSize") { pageSize = it.toInt() }
        request.useRequestParam("pageNum") { pageNum = it.toInt() }
    }
}
