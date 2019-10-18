package com.tencent.bkrepo.docker.rest.common.exception

class RestException : RuntimeException {
    var statusCode = 500

    constructor(message: String) : super(message) {}

    constructor(statusCode: Int, message: String) : super(message) {
        this.statusCode = statusCode
    }
}
