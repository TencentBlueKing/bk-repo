package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.fs.server.getOrNull
import com.tencent.bkrepo.fs.server.useRequestParam
import org.springframework.web.reactive.function.server.ServerRequest

class DriveNodePageRequest(request: ServerRequest) : DriveNodeRequest(request) {
    val parent: Long? = try {
        request.queryParam("parent").getOrNull()?.toLong()
    } catch (_: NumberFormatException) {
        throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "ino")
    }
    var pageSize: Int = DEFAULT_PAGE_SIZE
    val lastName: String? = request.queryParam("lastName").getOrNull()
    val lastId: String? = request.queryParam("lastId").getOrNull()
    val snapSeq: Long? = try {
        request.queryParam("snapSeq").getOrNull()?.toLong()
    } catch (_: NumberFormatException) {
        throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "snapSeq")
    }

    init {
        request.useRequestParam("pageSize") { pageSize = it.toInt() }
    }
}
