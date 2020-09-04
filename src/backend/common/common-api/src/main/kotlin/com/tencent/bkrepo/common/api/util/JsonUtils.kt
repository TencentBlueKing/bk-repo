package com.tencent.bkrepo.common.api.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter.ISO_DATE
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.time.format.DateTimeFormatter.ISO_TIME

/**
 * Json工具类
 */
object JsonUtils {
    val objectMapper = jacksonObjectMapper().apply {
        val javaTimeModule = JavaTimeModule()

        javaTimeModule.addSerializer(LocalTime::class.java, LocalTimeSerializer(ISO_TIME))
        javaTimeModule.addSerializer(LocalDate::class.java, LocalDateSerializer(ISO_DATE))
        javaTimeModule.addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer(ISO_DATE_TIME))
        javaTimeModule.addDeserializer(LocalTime::class.java, LocalTimeDeserializer(ISO_TIME))
        javaTimeModule.addDeserializer(LocalDate::class.java, LocalDateDeserializer(ISO_DATE))
        javaTimeModule.addDeserializer(LocalDateTime::class.java, LocalDateTimeDeserializer(ISO_DATE_TIME))
        registerModule(javaTimeModule)
        registerModule(ParameterNamesModule())
        registerModule(Jdk8Module())

        enable(SerializationFeature.INDENT_OUTPUT)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    }
}

/**
 * 将对象序列化为json字符串
 */
fun Any.toJsonString() = JsonUtils.objectMapper.writeValueAsString(this).orEmpty()

/**
 * 将json字符串反序列化为对象
 */
inline fun <reified T> String.readJsonString(): T = JsonUtils.objectMapper.readValue(this, jacksonTypeRef<T>())

/**
 * 将json字符串流反序列化为对象
 */
inline fun <reified T> InputStream.readJsonString(): T = JsonUtils.objectMapper.readValue(this, jacksonTypeRef<T>())
