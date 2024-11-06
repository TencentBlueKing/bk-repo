package com.tencent.bkrepo.maven.exception

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.MessageCode

class MavenBadRequestException(
    messageCode: MessageCode, vararg params: String
) : ErrorCodeException(messageCode = messageCode, params = params)
