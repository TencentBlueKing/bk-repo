package com.tencent.bkrepo.common.api.exception

import com.netflix.hystrix.exception.HystrixBadRequestException

/**
 * 外部ErrorCodeException。
 * 调用外部服务时，出现业务异常会返回errorCode和message，message已经过国际化与模版化，所以直接返回。
 */
open class ExternalErrorCodeException(
    val methodKey: String,
    val errorCode: Int,
    val errorMessage: String?
) : HystrixBadRequestException(errorMessage)
