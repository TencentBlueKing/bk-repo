package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.fs.server.useRequestParam
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.server.ServerRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class DriveNodeModifiedPageRequest(request: ServerRequest) : DriveNodeRequest(request) {
    val dateTimeParam: String = request.queryParam(DriveNodeModifiedPageRequest::lastModifiedDate.name)
        .orElseThrow {
            ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, DriveNodeModifiedPageRequest::lastModifiedDate.name)
        }
    val lastModifiedDate: LocalDateTime
    var pageSize: Int = DEFAULT_PAGE_SIZE
    var pageNum: Int = DEFAULT_PAGE_NUMBER
    var includeTotalRecords: Boolean = false

    init {
        try {
            lastModifiedDate = LocalDateTime.parse(dateTimeParam, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: DateTimeParseException) {
            if (logger.isDebugEnabled) {
                logger.debug("parse lastModifiedDate failed", e)
            }
            throw ErrorCodeException(
                CommonMessageCode.PARAMETER_INVALID, DriveNodeModifiedPageRequest::lastModifiedDate.name
            )
        }
        request.useRequestParam("pageSize") { pageSize = it.toInt() }
        request.useRequestParam("pageNum") { pageNum = it.toInt() }
        request.useRequestParam("includeTotalRecords") { includeTotalRecords = it.toBoolean() }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveNodeModifiedPageRequest::class.java)
    }
}
