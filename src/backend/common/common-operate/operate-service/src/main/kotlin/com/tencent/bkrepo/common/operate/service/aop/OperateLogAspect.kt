/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.operate.service.aop

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.operate.api.OperateLogService
import com.tencent.bkrepo.common.operate.api.pojo.OperateEvent
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import javax.servlet.http.HttpServletRequest

@Component
@Aspect
class OperateLogAspect(
    private val operateLogService: OperateLogService
) {
    @Pointcut("execution(* com.tencent.bkrepo..controller..*.*(..))")
    fun operateLog() { return }

    @Around("operateLog()")
    fun doAfterReturning(joinPoint: ProceedingJoinPoint): Any? {
        var obj: Any? = null
        val servletRequestAttributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        val httpServletRequest: HttpServletRequest = servletRequestAttributes.request
        val userName = httpServletRequest.getAttribute("userId")
        val map = HashMap<String, Any>()
        map.put("requestParam", joinPoint.args)
        try {
            obj = joinPoint.proceed()
            map.put("messageCode", CommonMessageCode.SUCCESS.getCode())
        } catch (throwable: Throwable) {
            var code = (throwable as ErrorCodeException).messageCode.getCode()
            map.put("messageCode", code)
        }
        for (event in EventType.values()) {
            if (checkURI(httpServletRequest.requestURI, event.requestURI) &&
                event.method.equals(httpServletRequest.method)) {
                val eventDetail = OperateEvent(
                    type = event.name,
                    projectId = "",
                    repoName = "",
                    resourceKey = "",
                    userId = userName as String,
                    address = HttpContextHolder.getClientAddress(httpServletRequest),
                    data = map
                )
                operateLogService.saveEventAsync(eventDetail)
                break
            }
        }
        return obj
    }

    fun checkURI(uri: String, list: List<String>): Boolean {
        var result = true
        for (param in list) {
            if (!uri.contains(param)) {
                result = false
                break
            }
        }
        return result
    }
}
