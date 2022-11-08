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

package com.tencent.bkrepo.common.operate.service.util

import com.tencent.bkrepo.common.operate.api.annotation.Sensitive
import com.tencent.bkrepo.common.operate.api.handler.SensitiveHandler
import org.springframework.beans.BeanUtils
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

object DesensitizedUtils {
    private val handlerMap = ConcurrentHashMap<KClass<*>, Any>()
    private val parameterNameDiscoverer: ParameterNameDiscoverer = DefaultParameterNameDiscoverer()

    /**
     * 获取方法参数详情，对参数进行脱敏
     */
    fun convertMethodArgsToMap(method: Method, args: Array<Any?>, desensitize: Boolean = false): Map<String, Any> {
        val parameters = method.parameters
        val parameterNames = parameterNameDiscoverer.getParameterNames(method)

        val argsMap: MutableMap<String, Any> = HashMap(args.size)
        for (i in parameters.indices) {
            val parameter = parameters[i]
            val parameterName = parameterNames?.get(i) ?: parameter.name
            val arg = args[i]
            val sensitiveAnnotation = parameter.getAnnotation(Sensitive::class.java)
            argsMap[parameterName] = if (arg != null && desensitize) {
                if (sensitiveAnnotation == null) {
                    desensitizeObject(arg)
                } else {
                    handleSensitive(sensitiveAnnotation, arg)
                }
            } else {
                arg
            } ?: "null"
        }
        return argsMap
    }

    /**
     * 对[obj]内部字段进行脱敏
     */
    fun desensitizeObject(obj: Any): Any? {
        if (BeanUtils.isSimpleProperty(obj.javaClass) || obj.javaClass == String::class.java) {
            return obj
        }

        val sensitiveAnnotation = obj.javaClass.getAnnotation(Sensitive::class.java)
        return if (sensitiveAnnotation != null) {
            handleSensitive(sensitiveAnnotation, obj)
        } else if (obj is Map<*, *>) {
            obj.entries.associate { entry ->
                entry.key?.let { desensitizeObject(it) } to entry.value?.let { desensitizeObject(it) }
            }
        } else if (obj is Iterable<*>) {
            obj.filterNotNull().map { desensitizeObject(it) }
        } else if (obj is Array<*>) {
            obj.filterNotNull().map { desensitizeObject(it) }
        } else {
            ReflectionUtils.doWithFields(obj.javaClass) { desensitizeFieldOfObject(obj, it) }
            obj
        }
    }

    /**
     * 对对象[obj]的[field]字段进行脱敏
     */
    private fun desensitizeFieldOfObject(obj: Any, field: Field) {
        if (Modifier.isStatic(field.modifiers)) {
            // 不对静态变量进行脱敏
            return
        }
        val annotation = field.getAnnotation(Sensitive::class.java)
        ReflectionUtils.makeAccessible(field)
        field.get(obj)?.let { fieldValue ->
            val desensitizedFieldValue = if (annotation == null) {
                // 递归脱敏所有字段
                desensitizeObject(fieldValue)
            } else {
                handleSensitive(annotation, fieldValue)
            }
            field.set(obj, desensitizedFieldValue)
        }
    }

    private fun handleSensitive(annotation: Sensitive, arg: Any): Any? {
        val handler = handlerMap.getOrPut(annotation.handler) { annotation.handler.createInstance() }
        require(handler is SensitiveHandler)
        return handler.desensitize(arg)
    }
}
