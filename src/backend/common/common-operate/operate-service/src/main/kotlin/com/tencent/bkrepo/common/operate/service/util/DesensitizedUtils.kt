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
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.isSubclassOf

object DesensitizedUtils {
    private val handlerMap = ConcurrentHashMap<KClass<*>, Any>()
    private val parameterNameDiscoverer: ParameterNameDiscoverer = DefaultParameterNameDiscoverer()

    fun toString(obj: Any): String {
        return "${obj.javaClass.simpleName}=${desensitizeObject(obj)}"
    }

    /**
     * 获取方法参数详情，对参数进行脱敏
     */
    fun convertMethodArgsToMap(method: Method, args: Array<Any?>, desensitize: Boolean = false): Map<String, Any?> {
        val parameters = method.parameters
        val parameterNames = parameterNameDiscoverer.getParameterNames(method)

        val argsMap: MutableMap<String, Any?> = LinkedHashMap(args.size)
        for (i in parameters.indices) {
            val parameter = parameters[i]
            val parameterName = parameterNames?.get(i) ?: parameter.name
            val arg = args[i]
            argsMap[parameterName] = if (arg != null && desensitize) {
                desensitizeObject(arg, parameter.getAnnotation(Sensitive::class.java)?.handler)
            } else {
                arg
            }
        }
        return argsMap
    }

    /**
     * 对指定对象内容进行脱敏操作，返回脱敏后的结果
     *
     * @param obj 需要脱敏的对象
     * @param handlerClass 脱敏方式，会使用[handlerClass]对应的对象对[obj]进行脱敏，未指定时会遍历[obj]所有字段根据其注解进行脱敏
     * @return 传入的obj是列表或数组时返回List，传入的是普通对象时返回Map
     */
    @Suppress("ReturnCount")
    fun desensitizeObject(obj: Any, handlerClass: KClass<*>? = null): Any? {
        // 指定handler，直接使用handler脱敏后返回结果
        val handler = handlerClass ?: obj.javaClass.getAnnotation(Sensitive::class.java)?.handler
        handler?.let { return desensitize(it, obj) }

        // 不支持遍历字段的类型直接返回
        if (!shouldTravel(obj.javaClass)) {
            return obj
        }

        // 未指定handler，遍历所有字段
        return when (obj) {
            is Map<*, *> -> {
                val result = LinkedHashMap<Any?, Any?>()
                obj.entries.forEach { entry ->
                    result[entry.key?.let { desensitizeObject(it) }] = entry.value?.let { desensitizeObject(it) }
                }
                result
            }
            is Iterable<*> -> obj.filterNotNull().map { desensitizeObject(it) }
            is Array<*> -> obj.filterNotNull().map { desensitizeObject(it) }
            else -> {
                val result = LinkedHashMap<String, Any?>()
                ReflectionUtils.doWithFields(obj.javaClass) { field ->
                    // 不对静态变量进行脱敏
                    if (!Modifier.isStatic(field.modifiers)) {
                        ReflectionUtils.makeAccessible(field)
                        result[field.name] = field.get(obj)?.let {
                            // 递归脱敏所有字段
                            desensitizeObject(it, field.getAnnotation(Sensitive::class.java)?.handler)
                        }
                    }
                }
                result
            }
        }
    }

    private fun desensitize(handlerClass: KClass<*>, arg: Any): Any? {
        require(handlerClass.isSubclassOf(SensitiveHandler::class))
        val handler = handlerMap.getOrPut(handlerClass) { handlerClass.createInstance() }
        require(handler is SensitiveHandler)
        return handler.desensitize(arg)
    }

    /**
     * 是否需要遍历[clazz]的所有字段
     */
    private fun shouldTravel(clazz: Class<*>): Boolean {
        return !BeanUtils.isSimpleProperty(clazz) && clazz !in ignoredClasses
    }

    /**
     * 脱敏时需要忽略的类型，不会递归遍历这些类型的字段
     */
    private val ignoredClasses = arrayOf(String::class.java)
}
