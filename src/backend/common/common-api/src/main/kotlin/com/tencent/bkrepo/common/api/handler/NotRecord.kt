/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.api.handler

/**
 * 不记录敏感信息
 */
class NotRecord : AbsSensitiveHandler() {
    override fun doDesensitize(sensitiveObj: Any): Any? {
        return defaultValue(sensitiveObj::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> defaultValue(type: Class<T>): T? {
        return when (type) {
            java.lang.Boolean::class.java -> java.lang.Boolean.FALSE as T
            java.lang.Character::class.java -> Character.valueOf('\u0000') as T
            java.lang.Byte::class.java -> java.lang.Byte.valueOf(0.toByte()) as T
            java.lang.Short::class.java -> 0.toShort() as T
            java.lang.Integer::class.java -> Integer.valueOf(0) as T
            java.lang.Long::class.java -> java.lang.Long.valueOf(0L) as T
            java.lang.Float::class.java -> 0.0 as T
            java.lang.Double::class.java -> 0f as T
            else -> null
        }
    }
}
