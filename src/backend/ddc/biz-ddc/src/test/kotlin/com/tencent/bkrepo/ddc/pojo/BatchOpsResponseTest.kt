package com.tencent.bkrepo.ddc.pojo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.bkrepo.ddc.serialization.CbFieldType
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.utils.beginUniformArray
import com.tencent.bkrepo.ddc.utils.writeStringValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BatchOpsResponseTest {
    @Test
    fun test() {
        val response = CbObject.build { responseWriter ->
            responseWriter.beginUniformArray("needs", CbFieldType.String)
            responseWriter.writeStringValue("o1")
            responseWriter.writeStringValue("o2")
            responseWriter.endArray()
        }

        val opResponse1 = OpResponse(1, response, 0)
        val opResponse2 = OpResponse(2, response, 0)
        val opsResponse = BatchOpsResponse(listOf(opResponse1, opResponse2))
        val opsResCb = opsResponse.serialize()
        val json = opsResCb.toJson(jacksonObjectMapper())
        val expectedJson = "{\"results\":[" +
                "{\"opId\":1,\"response\":{\"needs\":[\"o1\",\"o2\"]},\"statusCode\":0}," +
                "{\"opId\":2,\"response\":{\"needs\":[\"o1\",\"o2\"]},\"statusCode\":0}]" +
                "}"
        assertEquals(expectedJson, json)
    }
}
