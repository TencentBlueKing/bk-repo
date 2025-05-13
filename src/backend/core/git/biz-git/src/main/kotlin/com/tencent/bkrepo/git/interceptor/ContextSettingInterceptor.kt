package com.tencent.bkrepo.git.interceptor

import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.git.context.DfsDataReaders
import com.tencent.bkrepo.git.context.DfsDataReadersHolder
import com.tencent.bkrepo.git.context.UserHolder
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 上下文拦截器,设置请求上下文
 * */
class ContextSettingInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        DfsDataReadersHolder.setDfsReader(DfsDataReaders())
        UserHolder.setUser(SecurityUtils.getUserId())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        DfsDataReadersHolder.reset()
        UserHolder.reset()
    }
}
