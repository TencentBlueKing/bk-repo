package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus

/**
 * 构件验证异常
 */
open class ArtifactValidateException(message: String) : ArtifactException(HttpStatus.BAD_REQUEST, message)
