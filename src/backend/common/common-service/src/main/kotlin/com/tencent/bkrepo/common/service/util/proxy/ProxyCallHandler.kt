package com.tencent.bkrepo.common.service.util.proxy

import okhttp3.Request
import okhttp3.Response
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

interface ProxyCallHandler {
    fun before(proxyRequest: HttpServletRequest, proxyResponse: HttpServletResponse, request: Request): Request

    fun after(proxyRequest: HttpServletRequest, proxyResponse: HttpServletResponse, response: Response)
}
