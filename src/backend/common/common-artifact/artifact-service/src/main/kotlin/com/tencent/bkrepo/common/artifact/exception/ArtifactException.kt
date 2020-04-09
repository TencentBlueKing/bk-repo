package com.tencent.bkrepo.common.artifact.exception

import org.springframework.http.HttpStatus

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class ArtifactException(
    override val message: String,
    val status: Int = HttpStatus.BAD_REQUEST.value()
) : RuntimeException(message)
