/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.s3.artifact.auth

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.s3.artifact.utils.AWS4AuthUtil
import java.util.concurrent.TimeUnit

/**
 * 认证验证器
 */
abstract class AbstractAuthValidator {

    private var nextValidator: AbstractAuthValidator? = null

    abstract fun loadSecretKey(accessKeyId: String): List<String>

    fun setNext(nextValidator: AbstractAuthValidator) {
        this.nextValidator = nextValidator
    }

    fun validate(authCredentials: AWS4AuthCredentials): Boolean {
        var pass: Boolean
        val secretKeys = loadSecretKey(authCredentials.accessKeyId)
        secretKeys.forEach {
            pass = AWS4AuthUtil.validAuthorization(authCredentials, it)
            if (pass) {
                secretKeyCache.put(authCredentials.accessKeyId, it)
                return true
            }
        }
        return nextValidator?.validate(authCredentials) ?: false
    }

    companion object {
        @JvmStatic
        protected val secretKeyCache: Cache<String, String> = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build()
        fun getAuthValidator(authenticationManager: AuthenticationManager): AbstractAuthValidator {
            val cacheAuthValidator = CacheAuthValidator()
            val passwordAuthValidator = PasswordAuthValidator(authenticationManager)
            val tokenAuthValidator = TokenAuthValidator(authenticationManager)

            cacheAuthValidator.setNext(passwordAuthValidator)
            passwordAuthValidator.setNext(tokenAuthValidator)
            return cacheAuthValidator
        }
    }
}
