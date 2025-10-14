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

package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.metadata.annotation.Sensitive
import com.tencent.bkrepo.common.metadata.handler.SensitiveHandler
import org.springframework.beans.BeanUtils
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.util.ConcurrentReferenceHashMap
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.time.Duration
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.isSubclassOf

object DesensitizedUtils {
    private val handlerCache = ConcurrentReferenceHashMap<KClass<*>, Any>()
    private val parameterNameDiscoverer: ParameterNameDiscoverer = DefaultParameterNameDiscoverer()

    /**
     * 缓存类所有字段的注解
     */
    private val classFieldsAnnotationCache = ConcurrentReferenceHashMap<Class<*>, Map<String, Sensitive?>>()

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
                val fieldsAnnotationMap = classFieldsAnnotationCache.getOrPut(obj.javaClass) {
                    getAllFieldsAnnotation(obj.javaClass, Sensitive::class.java)
                }
                ReflectionUtils.doWithFields(obj.javaClass) { field ->
                    // 不对静态变量进行脱敏，跳过子类存在的field
                    if (!Modifier.isStatic(field.modifiers) || result.contains(field.name)) {
                        ReflectionUtils.makeAccessible(field)
                        result[field.name] = field.get(obj)?.let {
                            // 递归脱敏所有字段
                            desensitizeObject(it, fieldsAnnotationMap[field.name]?.handler)
                        }
                    }
                }
                result
            }
        }
    }

    private fun desensitize(handlerClass: KClass<*>, arg: Any): Any? {
        require(handlerClass.isSubclassOf(SensitiveHandler::class))
        val handler = handlerCache.getOrPut(handlerClass) { handlerClass.createInstance() }
        require(handler is SensitiveHandler)
        return handler.desensitize(arg)
    }

    private fun <T : Annotation> getAllFieldsAnnotation(clazz: Class<*>, annotationClass: Class<T>): Map<String, T?> {
        val result = LinkedHashMap<String, T?>()
        var targetClass: Class<*>? = clazz
        while (targetClass != null && targetClass != Object::class.java) {
            val fields = targetClass.declaredFields
            val constructors = targetClass.constructors
            val ctorParamMap = constructors.associateWith {
                Pair(it.parameters, parameterNameDiscoverer.getParameterNames(it))
            }

            for (fieldIndex in fields.indices) {
                val field = fields[fieldIndex]
                // 子类的field优先级最高，跳过后续搜索，即使子类field的注解为null也会使用
                if (result.contains(field.name) || Modifier.isStatic(field.modifiers)) {
                    continue
                }

                // field上的注解存在时直接使用，否则遍历所有构造方法，判断是否有与field同名的参数带有注解
                result[field.name] = field.getAnnotation(annotationClass)
                    ?: getFieldAnnotationFromConstructor(constructors, ctorParamMap, annotationClass, field.name)
            }
            // 遍历所有父类
            targetClass = targetClass.superclass
        }
        return result
    }

    private fun <T : Annotation> getFieldAnnotationFromConstructor(
        constructors: Array<Constructor<*>>,
        constructorsParamMap: Map<Constructor<*>, Pair<Array<out Parameter>, Array<out String>?>>,
        annotationClass: Class<T>,
        fieldName: String
    ): T? {
        var result: T? = null
        for (constructIndex in constructors.indices) {
            val constructor = constructors[constructIndex]
            val constructorParams = constructorsParamMap[constructor]!!.first
            val paramNames = constructorsParamMap[constructor]!!.second ?: continue
            // 比哪里构造函数的所有参数
            for (paramIndex in constructorParams.indices) {
                val parameter = constructorParams[paramIndex]
                val paramName = paramNames[paramIndex]
                if (paramName == fieldName) {
                    // 存在同名参数，不再遍历后续参数
                    result = parameter.getAnnotation(annotationClass)
                    break
                }
            }
            // 找到注解后不再遍历后续构造方法
            if (result != null) {
                return result
            }
        }
        return result
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
    private val ignoredClasses = arrayOf(String::class.java, Duration::class.java)
}
