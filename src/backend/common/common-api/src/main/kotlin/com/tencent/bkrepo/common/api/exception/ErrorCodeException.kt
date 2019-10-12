package com.tencent.bkrepo.common.api.exception

/**
 * 根据错误码会反查错误信息，用于改造现有直接抛出一些错误的异常
 */
open class ErrorCodeException(val errorCode: Int, vararg val params: String, val defaultMessage: String? = null) : RuntimeException(defaultMessage)
