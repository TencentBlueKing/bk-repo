package com.tencent.bkrepo.common.artifact.exception

import org.springframework.http.HttpStatus

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class UnsupportedMethodException : ArtifactException("Method not allowed", HttpStatus.METHOD_NOT_ALLOWED.value())
