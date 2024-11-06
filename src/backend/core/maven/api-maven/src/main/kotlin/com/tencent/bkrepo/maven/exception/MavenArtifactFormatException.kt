package com.tencent.bkrepo.maven.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.MessageCode

class MavenArtifactFormatException(messageCode: MessageCode, vararg params: String) :
    ErrorCodeException(messageCode = messageCode, status = HttpStatus.NOT_ACCEPTABLE, params = params)

