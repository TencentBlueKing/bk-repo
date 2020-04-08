package com.tencent.bkrepo.common.artifact.exception.handler

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.exception.ArtifactException
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.http.HttpStatus

abstract class AbstractExceptionHandler {
    fun response(status: HttpStatus, exception: ArtifactException): Response<*> {
        LoggerHolder.logException(exception, exception.message)
        return ResponseBuilder.fail(status.value(), exception.message)
    }
}