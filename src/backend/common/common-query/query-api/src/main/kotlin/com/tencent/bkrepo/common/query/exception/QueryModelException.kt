package com.tencent.bkrepo.common.query.exception

/**
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
class QueryModelException : RuntimeException {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(cause: Throwable) : super(cause)
}
