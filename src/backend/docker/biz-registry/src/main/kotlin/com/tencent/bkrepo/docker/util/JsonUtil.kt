package com.tencent.bkrepo.docker.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.util.HashMap

class JsonUtil {

    companion object {
        private val mapper = ObjectMapper()

        @Throws(IOException::class)
        fun readTree(json: ByteArray): JsonNode {
            return mapper.readTree(json)
        }

        @Throws(IOException::class)
        fun <T> readValue(json: ByteArray, valueType: Class<T>): T {
            return mapper.readValue(json, valueType)
        }

        @Throws(IOException::class)
        fun <T> readValue(root: JsonNode, valueType: Class<T>): T {
            return mapper.readValue(root.binaryValue(), valueType)
        }

        @Throws(IOException::class)
        fun readMap(json: ByteArray): Map<String, Any> {
            return mapper.readValue(json, object : TypeReference<HashMap<String, Any>>() {
            })
        }

        @Throws(IOException::class)
        fun writeValue(`object`: Any): String {
            return mapper.writeValueAsString(`object`)
        }

        init {
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}
