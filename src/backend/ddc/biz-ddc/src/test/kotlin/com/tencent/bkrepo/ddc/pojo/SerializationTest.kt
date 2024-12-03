/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.ddc.pojo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.ddc.serialization.CbFieldType
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.serialization.CbWriter
import com.tencent.bkrepo.ddc.utils.BlakeUtils
import com.tencent.bkrepo.ddc.utils.BlakeUtils.hex
import com.tencent.bkrepo.ddc.utils.DdcUtils
import com.tencent.bkrepo.ddc.utils.beginUniformArray
import com.tencent.bkrepo.ddc.utils.writeBinaryAttachment
import com.tencent.bkrepo.ddc.utils.writeBool
import com.tencent.bkrepo.ddc.utils.writeInteger
import com.tencent.bkrepo.ddc.utils.writeString
import com.tencent.bkrepo.ddc.utils.writeStringValue
import com.tencent.bkrepo.ddc.utils.writerObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class SerializationTest {
    @Test
    fun testSerializeBatchOpsResponse() {
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

    @Test
    fun testDeserializeBatchOps() {
        val writer = CbWriter()
        writer.beginObject()
        writer.beginUniformArray(BatchOps::ops.name, CbFieldType.Object)
        writer.beginObject()
        writer.writeInteger(BatchOp::opId.name, 0)
        writer.writeString(BatchOp::bucket.name, "bucket")
        writer.writeString(BatchOp::key.name, "key")
        writer.writeString(BatchOp::op.name, Operation.GET.name)
        writer.writeBool(BatchOp::resolveAttachments.name, true)
        writer.endObject()

        writer.beginObject()
        writer.writeInteger(BatchOp::opId.name, 0)
        writer.writeString(BatchOp::bucket.name, "bucket")
        writer.writeString(BatchOp::key.name, "key")
        writer.writeString(BatchOp::op.name, Operation.HEAD.name)
        writer.endObject()

        val payload = CbObject.build { innerWriter -> innerWriter.writeString("test", "test value") }
        writer.beginObject()
        writer.writeInteger(BatchOp::opId.name, 0)
        writer.writeString(BatchOp::bucket.name, "bucket")
        writer.writeString(BatchOp::key.name, "key")
        writer.writeString(BatchOp::op.name, Operation.PUT.name)
        writer.writerObject(BatchOp::payload.name, payload)
        writer.writeString(BatchOp::payloadHash.name, "test hash")
        writer.endObject()
        writer.endArray()
        writer.endObject()

        val batchOps = BatchOps.deserialize(writer.toByteArray())
        println(batchOps.ops[2].payload!!.toJson(jacksonObjectMapper()))
        assertEquals(
            "{\"test\":\"test value\"}",
            batchOps.ops[2].payload!!.toJson(jacksonObjectMapper())
        )
    }

    @Test
    fun testSerializeCreateRefResponse() {
        val res = CreateRefResponse(needs = setOf("a", "b", "c"))
        assertEquals("{\"needs\":[\"a\",\"b\",\"c\"]}", res.serialize().toJson(jacksonObjectMapper()))
    }

    @Test
    fun testSerializeError() {
        val e = ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "test")
        val (error, code) = DdcUtils.toError(
            e, e.status.value, "[250105]system.parameter.invalid"
        )
        assertEquals(HttpStatus.BAD_REQUEST.value, code)
        assertEquals(
            "{\"title\":\"[250105]system.parameter.invalid\",\"status\":400}",
            error.toJson(jacksonObjectMapper())
        )
    }

    @Test
    fun testSerializeBatchOp() {
        val getOp = BatchOp(
            opId = 1,
            bucket = "testbucket",
            key = "testtkeyyytesttkeyyytesttkeyyytesttkeyyy",
            op = Operation.GET.name,
            resolveAttachments = true
        )
        val headOp = BatchOp(
            opId = 2,
            bucket = "testbucket",
            key = "testtkeyyytesttkeyyytesttkeyyytesttkeyyy",
            op = Operation.HEAD.name,
            resolveAttachments = false
        )
        val payload = CbObject.build {
            it.writeBinaryAttachment("test", "testttestttestttestt".toByteArray())
        }

        // 计算payload fieldData hash
        val cbFieldData = payload.innerField.fieldData
        val field = ByteBuffer::class.java.getDeclaredField("hb")
        field.isAccessible = true
        val payloadHash = BlakeUtils.hash(field.get(cbFieldData) as ByteArray).hex()
        val putOp = BatchOp(
            opId = 3,
            bucket = "testbucket",
            key = "testtkeyyytesttkeyyytesttkeyyytesttkeyyy",
            op = Operation.PUT.name,
            payload = payload,
            payloadHash = payloadHash,
        )
        val batchOps = BatchOps(listOf(getOp, headOp, putOp))
        assertEquals(407, batchOps.serialize().getSize())
    }
}
