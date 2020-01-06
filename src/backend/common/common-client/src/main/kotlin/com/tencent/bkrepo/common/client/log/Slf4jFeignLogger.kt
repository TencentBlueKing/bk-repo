package com.tencent.bkrepo.common.client.log

import feign.Request
import feign.Response
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * feign 统一Slf4j日志记录器
 *
 * @author: carrypan
 * @date: 2019/11/1
 */
class Slf4jFeignLogger : feign.Logger() {

    private val logger = LoggerFactory.getLogger(API_LOGGER_NAME)

    override fun logRequest(configKey: String, logLevel: Level, request: Request) {
        if (this.logger.isInfoEnabled) {
            super.logRequest(configKey, logLevel, request)
        }
    }

    @Throws(IOException::class)
    override fun logAndRebufferResponse(configKey: String, logLevel: Level, response: Response, elapsedTime: Long): Response {
        return if (this.logger.isInfoEnabled) super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime) else response
    }

    override fun log(configKey: String, format: String, vararg args: Any) {
        if (this.logger.isInfoEnabled) {
            this.logger.info(String.format(methodTag(configKey) + format, *args))
        }
    }
    companion object {
        private const val API_LOGGER_NAME = "FeignApiLogger"
    }
}
