package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.fs.server.getOrNull
import com.tencent.bkrepo.fs.server.useRequestParam
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.server.ServerRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class DriveNodeModifiedPageRequest(request: ServerRequest) : DriveNodeRequest(request) {
    val lastModifiedDateParam: String = request.queryParam("lastModifiedDate")
        .orElseThrow {
            ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "lastModifiedDate")
        }
    val lastModifiedDate: LocalDateTime
    var pageSize: Int = DEFAULT_PAGE_SIZE
    val lastId: String = request.queryParam("lastId")
        .orElseThrow { ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "lastId") }

    init {
        try {
            lastModifiedDate = LocalDateTime.parse(lastModifiedDateParam, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: DateTimeParseException) {
            if (logger.isDebugEnabled) {
                logger.debug("parse lastModifiedDate failed", e)
            }
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "lastModifiedDate")
        }
        request.useRequestParam("pageSize") { pageSize = it.toInt() }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveNodeModifiedPageRequest::class.java)
    }
}
