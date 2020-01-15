package com.tencent.bkrepo.common.service.log

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.API_LOGGER_NAME
import com.tencent.bkrepo.common.api.constant.BUSINESS_ERROR_LOGGER_NAME
import com.tencent.bkrepo.common.api.constant.JOB_LOGGER_NAME
import com.tencent.bkrepo.common.api.constant.MS_REQUEST_KEY
import com.tencent.bkrepo.common.api.constant.SYSTEM_ERROR_LOGGER_NAME
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
     * 系统错误logger
     */
    val sysErrorLogger: Logger = LoggerFactory.getLogger(SYSTEM_ERROR_LOGGER_NAME)
    /**
     * 业务错误logger
     */
    val bizErrorLogger: Logger = LoggerFactory.getLogger(BUSINESS_ERROR_LOGGER_NAME)
    /**
     * 定时任务logger
     */
    val jobLogger: Logger = LoggerFactory.getLogger(JOB_LOGGER_NAME)
    /**
     * API logger
     */
    val apiLogger: Logger = LoggerFactory.getLogger(API_LOGGER_NAME)

    fun logException(exception: Exception, message: String? = "", logger: Logger = bizErrorLogger, logDetail: Boolean = false) {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val userId = request?.getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val accessChannel = determineAccessChannel(request)
        val uri = request?.requestURI
        val fullMessage = "User[$userId] access [$uri] by [$accessChannel] failed[${exception.javaClass.simpleName}]: $message"
        if (logDetail) {
            logger.error(fullMessage, exception)
        } else {
            logger.error(fullMessage)
        }
    }

    private fun determineAccessChannel(request: HttpServletRequest?) : String {
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
