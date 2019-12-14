package com.tencent.bkrepo.common.api.exception

import com.tencent.bkrepo.common.api.message.MessageCode

open class ErrorCodeException(val messageCode: MessageCode, vararg val params: String) : RuntimeException()
