package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus

/**
 * 构件接收异常
 */
open class ArtifactReceiveException(message: String) : ArtifactException(HttpStatus.BAD_REQUEST, "Receive artifact stream failed: $message")
