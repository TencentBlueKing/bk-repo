package com.tencent.bkrepo.common.artifact.exception

import org.springframework.http.HttpStatus

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class ArtifactNotFoundException(message: String) : ArtifactException(message, HttpStatus.NOT_FOUND.value())
