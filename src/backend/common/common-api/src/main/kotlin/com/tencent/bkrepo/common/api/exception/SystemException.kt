package com.tencent.bkrepo.common.api.exception

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 系统异常
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
open class SystemException(messageCode: MessageCode, vararg params: String) : ErrorCodeException(messageCode, *params)
