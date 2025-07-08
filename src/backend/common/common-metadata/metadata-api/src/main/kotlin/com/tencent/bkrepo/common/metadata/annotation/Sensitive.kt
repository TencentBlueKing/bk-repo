/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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


package com.tencent.bkrepo.common.metadata.annotation

import com.tencent.bkrepo.common.metadata.handler.NotRecord
import java.lang.annotation.Inherited
import kotlin.reflect.KClass

/**
 * 用于标记敏感信息字段的注解，当对象本身与其字段都包含该注解时，仅对象本身的注解生效
 * 例如下方的例子中，仅User类上的Sensitive注解会生效
 *
 * @Sensitive(handler = UserHandler::class)
 * class User {
 *     @Sensitive(handler = PasswordHandler::class)
 *     val password: String
 * }
 *
 * 方法参数与参数类型都有Sensitive注解时，方法参数的注解生效，如下方例子中create方法的Sensitive注解会生效
 *
 * fun create(@Sensitive(handler = CreateHandler::class) user: User) {}
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Inherited
@MustBeDocumented
annotation class Sensitive(val handler: KClass<*> = NotRecord::class)
