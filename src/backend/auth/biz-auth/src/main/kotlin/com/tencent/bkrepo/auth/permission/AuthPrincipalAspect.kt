/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.auth.permission

import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

@Aspect
class AuthPrincipalAspect(
    private val permissionService: PermissionService
) {

    @Around(
        "@within(com.tencent.bkrepo.auth.permission.AuthPrincipal) " +
            "|| @annotation(com.tencent.bkrepo.auth.permission.AuthPrincipal)"
    )
    @Throws(Throwable::class)
    fun around(point: ProceedingJoinPoint): Any? {
        val signature = point.signature
        require(signature is MethodSignature)
        val method = signature.method
        val principal = resolveFromMethod(method) ?: resolveFromClass(point.target.javaClass)
        val request = HttpContextHolder.getRequest()
        val userId = SecurityUtils.getUserId()
        val platId = SecurityUtils.getPlatformId()
        val checkRequest = CheckPermissionRequest(
            uid = userId,
            resourceType = ResourceType.SYSTEM.toString(),
            action = PermissionAction.WRITE.toString(),
            appId = platId
        )
        if (!permissionService.checkPermission(checkRequest)) {
            logger.warn("check user permission error [$checkRequest]")
            throw ErrorCodeException(AuthMessageCode.AUTH_PERMISSION_FAILED)
        }
        return null
    }

    private fun resolveFromClass(javaClass: Class<Any>): AuthPrincipal {
        var principal = javaClass.getAnnotation(AuthPrincipal::class.java)
        if (principal == null) { // 获取接口上的注解
            for (clazz in javaClass.interfaces) {
                principal = clazz.getAnnotation(AuthPrincipal::class.java)
                if (principal != null) {
                    break
                }
            }
        }
        return principal
    }

    private fun resolveFromMethod(method: Method?): AuthPrincipal? {
        return method?.getAnnotation(AuthPrincipal::class.java)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthPrincipalAspect::class.java)
    }
}
