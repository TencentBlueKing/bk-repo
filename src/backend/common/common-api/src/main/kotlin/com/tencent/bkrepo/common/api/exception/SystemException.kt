package com.tencent.bkrepo.common.api.exception

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 系统异常
 */
open class SystemException(messageCode: MessageCode, vararg params: String) : ErrorCodeException(messageCode, *params)
