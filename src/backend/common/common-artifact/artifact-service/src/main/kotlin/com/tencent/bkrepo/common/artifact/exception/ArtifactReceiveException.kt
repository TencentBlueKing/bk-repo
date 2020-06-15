package com.tencent.bkrepo.common.artifact.exception

import org.springframework.http.HttpStatus

open class ArtifactReceiveException(message: String) : ArtifactException("Receive artifact stream failed: $message", HttpStatus.BAD_REQUEST.value())
