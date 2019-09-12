
package com.tencent.bkrepo.registry.jacksonUtil

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

object JacksonUtil {
    fun createObjectMapper(): ObjectMapper {
        val objectMapper = ObjectMapper()
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true)
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        return objectMapper
    }
}
