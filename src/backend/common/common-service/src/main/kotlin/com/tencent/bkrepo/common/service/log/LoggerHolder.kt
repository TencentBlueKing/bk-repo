package com.tencent.bkrepo.common.service.log

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author: carrypan
 * @date: 2020/1/6
 */
object LoggerHolder {
    /**
     * 系统错误logger
     */
    val SYSTEM: Logger = LoggerFactory.getLogger("SystemErrorLogger")
    /**
     * 业务错误logger
     */
    val BUSINESS: Logger = LoggerFactory.getLogger("BusinessErrorLogger")
    /**
     * 定时任务logger
     */
    val JOB: Logger = LoggerFactory.getLogger("JoboLogger")

    fun logException(exception: Exception, message: String? = "", logger: Logger = BUSINESS, logDetail: Boolean = false) {
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = HttpContextHolder.getRequest().requestURI
        val fullMessage = "User[$userId] access [$uri] failed[${exception.javaClass.simpleName}]: $message"
        if(logDetail) {
            logger.error(fullMessage, exception)
        } else {
            logger.error(fullMessage)
        }
    }
}
