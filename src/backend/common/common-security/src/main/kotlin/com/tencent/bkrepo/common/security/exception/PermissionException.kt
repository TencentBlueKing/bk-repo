package com.tencent.bkrepo.common.security.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.StatusCodeException
import com.tencent.bkrepo.common.security.constant.PERMISSION_PROMPT

/**
 * 权限异常, 403错误
 */
open class PermissionException(message: String = PERMISSION_PROMPT) : StatusCodeException(HttpStatus.FORBIDDEN, message)
