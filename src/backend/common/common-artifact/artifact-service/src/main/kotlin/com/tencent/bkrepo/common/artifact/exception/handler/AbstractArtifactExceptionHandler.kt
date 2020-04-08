package com.tencent.bkrepo.common.artifact.exception.handler

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.exception.ArtifactException
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder

abstract class AbstractArtifactExceptionHandler {

    fun response(exception: ArtifactException): Response<*> {
        LoggerHolder.logBusinessException(exception)
        HttpContextHolder.getResponse().status = exception.status
        return ResponseBuilder.fail(exception.status, exception.message)
    }
}
