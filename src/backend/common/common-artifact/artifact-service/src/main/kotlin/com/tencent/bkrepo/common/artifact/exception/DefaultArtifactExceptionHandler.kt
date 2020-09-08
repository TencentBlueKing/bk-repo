package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.service.log.LoggerHolder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class DefaultArtifactExceptionHandler {

    /**
     * 构件接收失败，response被关闭，不进行响应
     */
    @ExceptionHandler(ArtifactReceiveException::class)
    fun handleException(exception: ArtifactReceiveException) {
        LoggerHolder.logBusinessException(exception)
    }
}
