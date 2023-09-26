package com.tencent.bkrepo.common.service.util.proxy

import okhttp3.Request
import okhttp3.Response
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

open class DefaultProxyCallHandler : ProxyCallHandler {
    override fun before(
        proxyRequest: HttpServletRequest,
        proxyResponse: HttpServletResponse,
        request: Request
    ): Request {
        return request
    }

    override fun after(proxyRequest: HttpServletRequest, proxyResponse: HttpServletResponse, response: Response) {
        // 转发状态码
        proxyResponse.status = response.code
        // 转发头
        response.headers.forEach { (key, value) -> proxyResponse.addHeader(key, value) }

        // 转发body
        response.body?.byteStream()?.use {
            it.copyTo(proxyResponse.outputStream)
        }
    }
}
