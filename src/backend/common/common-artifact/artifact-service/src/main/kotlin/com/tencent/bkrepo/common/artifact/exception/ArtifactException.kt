package com.tencent.bkrepo.common.artifact.exception

import java.lang.RuntimeException

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class ArtifactException : RuntimeException {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(cause: Throwable) : super(cause)
}
