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

package com.tencent.bkrepo.preview.utils

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.slf4j.LoggerFactory
import java.io.IOException
import java.text.SimpleDateFormat

/**
 * 简单封装Jackson，定制不同的输出
 */
class JsonMapper @JvmOverloads constructor(include: Include? = null) {

    private val mapper: ObjectMapper = ObjectMapper()

    init {
        // 设置输出时包含属性的风格
        include?.let { mapper.setSerializationInclusion(it) }
        // 设置输入时忽略在JSON字符串中存在但Java对象实际没有的属性
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        mapper.dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JsonMapper::class.java)

        // 非空属性的序列化
        fun nonEmptyMapper(): JsonMapper = JsonMapper(Include.NON_EMPTY)

        // 非默认值属性的序列化
        fun nonDefaultMapper(): JsonMapper = JsonMapper(Include.NON_DEFAULT)

        // 全部属性序列化
        fun getAllOutputMapper(): JsonMapper = JsonMapper(Include.ALWAYS)
    }

    /**
     * 将对象转换为 JSON 字符串
     */
    fun toJson(obj: Any?): String? {
        return try {
            mapper.writeValueAsString(obj)
        } catch (e: IOException) {
            logger.warn("object to json string error.", e);
            null
        }
    }

    /**
     * 反序列化 POJO 或简单集合，如 List<String>。
     * 如果 JSON 字符串为 Null 或 "null" 字符串，返回 Null。
     * 如果 JSON 字符串为 "[]"，返回空集合。
     * 如需反序列化复杂集合，如 List<MyBean>，请使用 fromJson(String, TypeReference)。
     */
    fun <T> fromJson(jsonString: String?, clazz: Class<T>): T? {
        if (jsonString.isNullOrEmpty()) return null

        return try {
            mapper.readValue(jsonString, clazz)
        } catch (e: IOException) {
            logger.warn("Parse json string error.", e)
            null
        }
    }

    /**
     * 反序列化复杂的泛型对象
     * 如果 JSON 字符串为 Null 或 "null" 字符串，返回 Null。
     * 如果 JSON 字符串为 "[]"，返回空集合。
     */
    fun <T> fromJson(jsonString: String?, typeReference: TypeReference<T>): T? {
        if (jsonString.isNullOrEmpty()) return null

        return try {
            mapper.readValue(jsonString, typeReference)
        } catch (e: IOException) {
            logger.warn("Parse json string error.", e)
            null
        }
    }

    /**
     * 当 JSON 里只含有 Bean 的部分属性时，更新一个已存在 Bean，只覆盖该部分的属性。
     */
    fun update(jsonString: String?, obj: Any) {
        try {
            mapper.readerForUpdating(obj).readValue(jsonString)
        } catch (e: IOException) {
            logger.warn("Update json string to object error.", e)
        }
    }

    /**
     * 获取底层的 ObjectMapper，用于进一步的设置或使用其他序列化 API。
     */
    fun getMapper(): ObjectMapper = mapper
}