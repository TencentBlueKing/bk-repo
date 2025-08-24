package com.tencent.bkrepo.media.job.k8s

import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoreV1Api
import org.springframework.http.HttpStatus

fun ApiException.buildMessage(): String {
    val builder = StringBuilder().append(code)
        .appendLine("[$message]")
        .appendLine(responseHeaders)
        .appendLine(responseBody)
    return builder.toString()
}

fun <T> CoreV1Api.exec(block: () -> T?): T? {
    try {
        return block()
    } catch (e: ApiException) {
        if (e.code == HttpStatus.NOT_FOUND.value()) {
            return null
        }
        throw e
    }
}
