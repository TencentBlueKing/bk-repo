/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.aspect

import com.tencent.bkrepo.common.api.constant.MS_REQUEST_KEY
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.springframework.aop.support.AbstractPointcutAdvisor
import org.springframework.aop.support.StaticMethodMatcherPointcut
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST
import org.springframework.web.context.request.RequestContextHolder
import java.lang.reflect.Method

@Component
class ServiceAspect : AbstractPointcutAdvisor() {

    private val pointcut = object : StaticMethodMatcherPointcut() {
        override fun matches(method: Method, targetClass: Class<*>): Boolean {
            return AnnotatedElementUtils.hasAnnotation(targetClass, FeignClient::class.java)
        }
    }

    /**
     * Feign调用在单体包部署时微服务调用全部变成方法调用，缺少部分微服务部署时会设置的参数，需要统一加上
     */
    private val interceptor = object : MethodInterceptor {
        override fun invoke(invocation: MethodInvocation): Any? {
            val requestAttributesExists = RequestContextHolder.getRequestAttributes() != null
            try {
                if (!requestAttributesExists) {
                    RequestContextHolder.setRequestAttributes(ServiceAttributes())
                }
                RequestContextHolder.getRequestAttributes()!!.setAttribute(MS_REQUEST_KEY, true, SCOPE_REQUEST)
                return invocation.proceed()
            } finally {
                // 需要在方法执行结束后移除，避免方法执行结束后影响后续执行逻辑
                RequestContextHolder.getRequestAttributes()!!.removeAttribute(MS_REQUEST_KEY, SCOPE_REQUEST)
                if (!requestAttributesExists) {
                    RequestContextHolder.resetRequestAttributes()
                }
            }
        }
    }

    override fun getPointcut() = pointcut

    override fun getAdvice() = interceptor

    /**
     * 用于模拟服务间调用时传递属性
     */
    private class ServiceAttributes : RequestAttributes {
        private val attributes: MutableMap<String, Any?> by lazy { HashMap() }

        override fun getAttribute(name: String, scope: Int): Any? {
            return attributes[name]
        }

        override fun setAttribute(name: String, value: Any, scope: Int) {
            attributes[name] = value
        }

        override fun removeAttribute(name: String, scope: Int) {
            attributes.remove(name)
        }

        override fun getAttributeNames(scope: Int): Array<String> {
            return attributes.keys.toTypedArray()
        }

        override fun registerDestructionCallback(name: String, callback: Runnable, scope: Int) {
            throw UnsupportedOperationException()
        }

        override fun resolveReference(key: String): Any? {
            throw UnsupportedOperationException()
        }

        override fun getSessionId(): String {
            throw UnsupportedOperationException()
        }

        override fun getSessionMutex(): Any {
            throw UnsupportedOperationException()
        }
    }
}
