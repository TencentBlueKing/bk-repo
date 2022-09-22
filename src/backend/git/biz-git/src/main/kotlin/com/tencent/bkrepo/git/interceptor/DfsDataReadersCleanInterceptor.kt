package com.tencent.bkrepo.git.interceptor

import com.tencent.bkrepo.git.context.DfsDataReadersHolder
import com.tencent.bkrepo.git.context.DfsDataReaders
import java.lang.Exception
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

/**
 * DfsDataReaders清理拦截器，负责DfsDataReaders的清理
 * */
class DfsDataReadersCleanInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        DfsDataReadersHolder.setDfsReader(DfsDataReaders())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        DfsDataReadersHolder.reset()
    }
}
