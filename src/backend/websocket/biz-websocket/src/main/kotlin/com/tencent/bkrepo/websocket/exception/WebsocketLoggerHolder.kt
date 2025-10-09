package com.tencent.bkrepo.websocket.exception

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.EXCEPTION_LOGGER_NAME
import com.tencent.bkrepo.common.api.constant.PLATFORM_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessageHeaderAccessor


object WebsocketLoggerHolder {

    /**
     * 异常logger
     */
    val exceptionLogger: Logger = LoggerFactory.getLogger(EXCEPTION_LOGGER_NAME)


    fun logErrorCodeException(accessor: SimpMessageHeaderAccessor, exception: ErrorCodeException, message: String) {
        val systemError = exception.status.isServerError()
        logException(accessor, exception, message, systemError)
    }

    fun logException(
        accessor: SimpMessageHeaderAccessor,
        exception: Exception,
        message: String?,
        systemError: Boolean
    ) {
        val userId = accessor.sessionAttributes?.get(USER_KEY) ?: ANONYMOUS_USER
        val dest = accessor.destination
        val platformId = accessor.sessionAttributes?.get(PLATFORM_KEY)
        val principal = platformId?.let { "$it-$userId" } ?: userId
        val exceptionMessage = message ?: exception.message.orEmpty()
        val exceptionName = exception.javaClass.simpleName
        val cause = if (exception is ErrorCodeException && exception.cause != null) {
            exception.cause
        } else {
            exception
        }
        val fullMessage = "User[$principal] send msg to destination[$dest] failed[$exceptionName]: $exceptionMessage"
        if (systemError) {
            exceptionLogger.error(fullMessage, cause)
        } else {
            exceptionLogger.warn(fullMessage)
        }
    }
}