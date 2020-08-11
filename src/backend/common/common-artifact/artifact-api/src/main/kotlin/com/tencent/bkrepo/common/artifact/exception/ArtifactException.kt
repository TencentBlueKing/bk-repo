package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.StatusCodeException

/**
 * 构件相关异常
 */
open class ArtifactException(
    override val status: HttpStatus = HttpStatus.BAD_REQUEST,
    override val message: String? = null
) : StatusCodeException(status, message)
