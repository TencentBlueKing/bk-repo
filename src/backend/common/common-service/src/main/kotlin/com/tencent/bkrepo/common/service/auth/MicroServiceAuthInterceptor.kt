package com.tencent.bkrepo.common.service.auth

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.MS_REQUEST_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import org.springframework.core.annotation.Order
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 微服务调用认证信息提取
 *
 * @author: carrypan
 * @date: 2019/12/23
 */
@Order(0)
class MicroServiceAuthInterceptor : HandlerInterceptorAdapter() {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.getHeader(MS_AUTH_HEADER_UID)?.run {
            request.setAttribute(USER_KEY, this)
            request.setAttribute(MS_REQUEST_KEY, true)
        }
        return true
    }
}
