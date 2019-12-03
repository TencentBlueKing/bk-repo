package com.tencent.bkrepo.common.artifact.exception

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class ArtifactValidateException: ArtifactException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(cause: Throwable) : super(cause)
}