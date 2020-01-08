package com.tencent.bkrepo.common.api.util

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 *
 * @author: carrypan
 * @date: 2020/1/6
 */
object LoggerHolder {
    /**
     * 系统错误logger
     */
    val SYSTEM_ERROR: Logger = LoggerFactory.getLogger("SystemErrorLogger")
    /**
     * 业务错误logger
     */
    val BUSINESS_ERROR: Logger = LoggerFactory.getLogger("BusinessErrorLogger")
    /**
     * 定时任务logger
     */
    val JOB: Logger = LoggerFactory.getLogger("JobLogger")
    /**
     * API logger
     */
    val API: Logger = LoggerFactory.getLogger("FeignApiLogger")

    fun logException(exception: Exception, message: String? = "", logger: Logger = BUSINESS_ERROR, logDetail: Boolean = false) {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val userId = request?.getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = request?.requestURI
        val fullMessage = "User[$userId] access [$uri] failed[${exception.javaClass.simpleName}]: $message"
        if(logDetail) {
            logger.error(fullMessage, exception)
        } else {
            logger.error(fullMessage)
        }
    }
}
