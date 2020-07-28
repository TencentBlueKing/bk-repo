package com.tencent.bkrepo.common.api.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

object JsonUtils {
    val objectMapper = jacksonObjectMapper().apply {
        val javaTimeModule = JavaTimeModule()
        javaTimeModule.addDeserializer(LocalDateTime::class.java, LocalDateTimeDeserializer(ISO_DATE_TIME))
        javaTimeModule.addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer(ISO_DATE_TIME))
        registerModule(javaTimeModule)
        registerModule(ParameterNamesModule())
        registerModule(Jdk8Module())

        configure(SerializationFeature.INDENT_OUTPUT, true)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    }
}

fun Any.toJsonString() = JsonUtils.objectMapper.writeValueAsString(this).orEmpty()
