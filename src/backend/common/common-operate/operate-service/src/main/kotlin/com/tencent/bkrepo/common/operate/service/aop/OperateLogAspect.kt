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

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.operate.api.OperateLogService
import com.tencent.bkrepo.common.operate.api.pojo.OperateEvent
import com.tencent.bkrepo.common.operate.service.annotation.OperateLog
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import javax.servlet.http.HttpServletRequest

@Component
@Aspect
class OperateLogAspect(
    private val operateLogService: OperateLogService
) {
    @Around(
        "@within(com.tencent.bkrepo.common.operate.service.annotation.OperateLog) " +
            "|| @annotation(com.tencent.bkrepo.common.operate.service.annotation.OperateLog)"
    )
    @Throws(Throwable::class)
    fun around(joinPoint: ProceedingJoinPoint): Any? {
        var obj: Any? = null
        val servletRequestAttributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        val httpServletRequest: HttpServletRequest = servletRequestAttributes.request
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val discoverer = DefaultParameterNameDiscoverer()
        val parameterNames = discoverer.getParameterNames(method)
        val annotation = method.getAnnotation(OperateLog::class.java)
        val userId = httpServletRequest.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
        val map = HashMap<String, Any>()
        if (parameterNames != null && parameterNames.isNotEmpty()) {
            val paramMap = HashMap<String, Any>()
            for (i in parameterNames.indices) {
                paramMap[parameterNames.get(i)] = joinPoint.args.get(i)
            }
            map["requestParam"] = paramMap
        } else {
            map["requestParam"] = joinPoint.args
        }
        try {
            obj = joinPoint.proceed()
            map["messageCode"] = CommonMessageCode.SUCCESS.getCode()
        } catch (errorCodeException: ErrorCodeException) {
            map["messageCode"] = errorCodeException.messageCode.getCode()
        }
        val eventDetail = OperateEvent(
                    type = annotation.name,
                    projectId = "",
                    repoName = "",
                    resourceKey = "",
                    userId = userId,
                    address = HttpContextHolder.getClientAddress(httpServletRequest),
                    data = map
                )
        operateLogService.saveEventAsync(eventDetail)
        return obj
    }
}
