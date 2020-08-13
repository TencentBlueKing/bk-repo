package com.tencent.bkrepo.common.security.exception

import com.tencent.bkrepo.common.security.constant.BAD_CREDENTIALS_PROMPT

/**
 * 身份凭证不合法
 *
 */
class BadCredentialsException(override val message: String = BAD_CREDENTIALS_PROMPT) : AuthenticationException(message)
