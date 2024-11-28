package com.tencent.bkrepo.ddc.pojo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.bkrepo.ddc.serialization.CbFieldType
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.serialization.CbWriter
import com.tencent.bkrepo.ddc.utils.beginUniformArray
import com.tencent.bkrepo.ddc.utils.writeBool
import com.tencent.bkrepo.ddc.utils.writeInteger
import com.tencent.bkrepo.ddc.utils.writeString
import com.tencent.bkrepo.ddc.utils.writerObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BatchOpsTest {
    @Test
    fun test() {
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
}
