package com.tencent.bkrepo.common.api.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus

/**
 * Http 状态码异常
 */
open class StatusCodeException(
    open val status: HttpStatus,
    open val reason: String? = null
) : RuntimeException(reason ?: status.reasonPhrase)
