package com.tencent.bkrepo.common.service.util.proxy

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import okhttp3.Response

interface ProxyCallHandler {
    fun after(proxyRequest: HttpServletRequest, proxyResponse: HttpServletResponse, response: Response)
}
