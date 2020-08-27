package com.tencent.bkrepo.common.security.exception

import com.tencent.bkrepo.common.security.constant.ACCESS_DENIED_PROMPT

/**
 * 禁止访问
 */
class AccessDeniedException : PermissionException(ACCESS_DENIED_PROMPT)
