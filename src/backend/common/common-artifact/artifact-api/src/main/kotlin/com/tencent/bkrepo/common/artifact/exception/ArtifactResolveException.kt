package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus

/**
 * 构件解析异常
 */
open class ArtifactResolveException(message: String) : ArtifactException(HttpStatus.BAD_REQUEST, message)
