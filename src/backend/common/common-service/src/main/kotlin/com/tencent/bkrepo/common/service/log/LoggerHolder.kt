package com.tencent.bkrepo.common.service.log

import com.tencent.bkrepo.common.api.constant.ACCESS_LOGGER_NAME
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.EXCEPTION_LOGGER_NAME
import com.tencent.bkrepo.common.api.constant.JOB_LOGGER_NAME
import com.tencent.bkrepo.common.api.constant.MS_REQUEST_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2020/1/6
 */
object LoggerHolder {
    /**
     * 异常logger
     */
    val exceptionLogger: Logger = LoggerFactory.getLogger(EXCEPTION_LOGGER_NAME)

    /**
     * Job logger
     */
    val jobLogger: Logger = LoggerFactory.getLogger(JOB_LOGGER_NAME)

    /**
     * Access logger
     */
    val accessLogger: Logger = LoggerFactory.getLogger(ACCESS_LOGGER_NAME)

    fun logBusinessException(exception: Exception, message: String? = null) {
        logException(exception, message, false)
    }

    fun logSystemException(exception: Exception, message: String? = null) {
        logException(exception, message, true)
    }

    private fun logException(exception: Exception, message: String?, systemError: Boolean) {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val userId = request?.getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val accessChannel = determineAccessChannel(request)
        val uri = request?.requestURI
        val method = request?.method
        val exceptionMessage = message ?: exception.message.orEmpty()
        val fullMessage = "User[$userId] $method [$uri] by [$accessChannel] failed[${exception.javaClass.simpleName}]: $exceptionMessage"
        if (systemError) {
            exceptionLogger.error(fullMessage, exception)
        } else {
            exceptionLogger.warn(fullMessage)
        }
    }

    private fun determineAccessChannel(request: HttpServletRequest?): String {
        return when {
            request == null -> {
                "None"
            }
            request.getAttribute(MS_REQUEST_KEY) as? Boolean == true -> {
                "MicroService"
            }
            else -> {
                "UserApi"
            }
        }
    }
}
