package com.tencent.bkrepo.common.artifact.exception.response

import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse

interface ExceptionResponseTranslator {
    /**
     * 转换异常消息响应体
     */
    fun translate(
        payload: Response<*>,
        request: ServerHttpRequest,
        response: ServerHttpResponse
    ): Any
}