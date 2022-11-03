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

import com.google.gson.Gson
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.operate.api.OperateLogService
import com.tencent.bkrepo.common.operate.api.pojo.OperateEvent
import com.tencent.bkrepo.common.operate.service.annotation.OperateLog
import com.tencent.bkrepo.common.operate.service.annotation.Sensitive
import com.tencent.bkrepo.common.operate.service.handler.ParamHandler
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
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
        record(joinPoint)
        return joinPoint.proceed()
    }

    fun record(joinPoint: ProceedingJoinPoint) {
        val servletRequestAttributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        val httpServletRequest: HttpServletRequest = servletRequestAttributes.request
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val annotation = method.getAnnotation(OperateLog::class.java)
        val eventDetail = OperateEvent(
            type = annotation.name,
            projectId = "",
            repoName = "",
            resourceKey = "",
            userId = httpServletRequest.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER,
            address = HttpContextHolder.getClientAddress(httpServletRequest),
            data = buildParamData(joinPoint)
        )
        operateLogService.saveEventAsync(eventDetail)
    }

    /**
     * 构建参数的map数据
     */
    fun buildParamData(joinPoint: ProceedingJoinPoint): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val annotations = method.parameterAnnotations

        for (i in annotations.indices) {
            val anos = annotations[i]
            if (anos.any() {
                    it.annotationClass.java.name.equals("org.springframework.web.bind.annotation.RequestBody") }) {
                map.putAll(handleBodyParam(joinPoint.args) as MutableMap<String, Any>)
            } else {
                map.putAll(handleFunctionParam(anos, method, i, joinPoint))
            }
        }
        return map
    }

    /**
     * 方法参数中的注解处理
     */
    fun handleFunctionParam(
        anos: Array<Annotation>,
        method: Method,
        i: Int,
        joinPoint: ProceedingJoinPoint
    ): Map<String, Any> {
        val discoverer = DefaultParameterNameDiscoverer()
        val parameterNames = discoverer.getParameterNames(method)
        val map = mutableMapOf<String, Any>()
        if (anos.any() {
                it.annotationClass.java.name.equals(
                    "com.tencent.bkrepo.common.operate.service.annotation.Sensitive")
            }) {
            val sensitive = anos.first { it.annotationClass.java.name.equals(
                "com.tencent.bkrepo.common.operate.service.annotation.Sensitive") } as Sensitive
            val handleMethodName: String = sensitive.handler
            val handleMethod: Method = ParamHandler::class.java.getMethod(handleMethodName, method.parameterTypes[i])
            val res = handleMethod.invoke(ParamHandler::class.java.newInstance(), joinPoint.args[i])
            map[parameterNames[i]] = res
        } else {
            map[parameterNames[i]] = if (joinPoint.args[i] == null) "" else joinPoint.args[i]
        }
        return map
    }

    /**
     * body中的参数处理
     */
    @Throws(NoSuchMethodException::class, IllegalAccessException::class, InvocationTargetException::class)
    fun handleBodyParam(objs: Array<Any>): Map<String, Any> {
        var map = mutableMapOf<String, Any>()
        for (obj in objs) {
            if (obj != null) {
                val fields = obj.javaClass.declaredFields
                val gson = Gson()
                val objMap = gson.fromJson(obj.toJsonString(), MutableMap::class.java)
                map.putAll(handleBodyField(fields, objMap))
            }
        }
        return map
    }

    /**
     * body中的属性注解处理
     */
    @Throws(NoSuchMethodException::class, IllegalAccessException::class, InvocationTargetException::class)
    fun handleBodyField(fields: Array<Field>, objMap: MutableMap<*, *>): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for (field in fields) {
            if (field.annotations.any() {
                    it.annotationClass.java.name.equals(
                        "com.tencent.bkrepo.common.operate.service.annotation.Sensitive")
            }) {
                val sensitive = field.getAnnotation(Sensitive::class.java)
                val handleMethodName: String = sensitive.handler
                val handlerMethod: Method = ParamHandler::class.java.getMethod(handleMethodName, field.type)
                val res = handlerMethod.invoke(ParamHandler::class.java, objMap.get(field.name))
                map.put(field.name, res)
            } else {
                objMap.get(field.name)?.let { map.put(field.name, it) }
            }
        }
        return map
    }
}
