package com.tencent.bkrepo.common.api.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream

/**
 * xml工具类
 */
object XmlUtils {
    val objectMapper: ObjectMapper = XmlMapper().apply {
        registerKotlinModule()
        enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
        enable(SerializationFeature.INDENT_OUTPUT)
    }
}

/**
 * 将对象序列化为xml字符串
 */
fun Any.toXmlString() = XmlUtils.objectMapper.writeValueAsString(this).orEmpty()

/**
 * 将xml字符串反序列化为对象
 */
inline fun <reified T> String.readXmlString(): T = XmlUtils.objectMapper.readValue(this, jacksonTypeRef<T>())

/**
 * 将xml字符串流反序列化为对象
 */
inline fun <reified T> InputStream.readXmlString(): T = XmlUtils.objectMapper.readValue(this, jacksonTypeRef<T>())