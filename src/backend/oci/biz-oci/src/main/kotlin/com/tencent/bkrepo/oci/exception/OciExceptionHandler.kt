package com.tencent.bkrepo.oci.exception

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.security.constant.BASIC_AUTH_PROMPT
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
class OciExceptionHandler {
	/**
	 * 单独处理认证失败异常，需要添加WWW_AUTHENTICATE响应头触发浏览器登录
	 */
	@ExceptionHandler(AuthenticationException::class)
	fun handleException(exception: AuthenticationException) {
		val response = HttpContextHolder.getResponse()
		response.status = HttpStatus.UNAUTHORIZED.value
		response.contentType = MediaTypes.APPLICATION_JSON
		response.setHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BASIC_AUTH_PROMPT)
		val errorMessage = LocaleMessageUtils.getLocalizedMessage(exception.messageCode, exception.params)
		LoggerHolder.logErrorCodeException(exception, "[${exception.messageCode.getCode()}]$errorMessage")
	}
}
