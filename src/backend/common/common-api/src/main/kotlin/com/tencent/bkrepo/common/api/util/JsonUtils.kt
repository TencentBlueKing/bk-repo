package com.tencent.bkrepo.common.api.util

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule

object JsonUtils {
    val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(ParameterNamesModule())
        registerModule(Jdk8Module())
        configure(SerializationFeature.INDENT_OUTPUT, true)
        configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    }
}
