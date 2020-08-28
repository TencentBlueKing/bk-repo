package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus

/**
 * 构件不存在异常
 */
open class ArtifactNotFoundException(message: String) : ArtifactException(HttpStatus.NOT_FOUND, message)
