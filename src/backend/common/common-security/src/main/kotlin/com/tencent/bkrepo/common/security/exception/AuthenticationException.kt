package com.tencent.bkrepo.common.security.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.StatusCodeException
import com.tencent.bkrepo.common.security.constant.AUTHORIZATION_PROMPT

/**
 * 用户认证异常, 401错误
 */
open class AuthenticationException(message: String = AUTHORIZATION_PROMPT) : StatusCodeException(HttpStatus.UNAUTHORIZED, message)
