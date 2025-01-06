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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.ddc.serialization.CbArray
import com.tencent.bkrepo.ddc.serialization.CbField
import com.tencent.bkrepo.ddc.serialization.CbFieldType
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.utils.beginUniformArray
import com.tencent.bkrepo.ddc.utils.isBool
import com.tencent.bkrepo.ddc.utils.isInteger
import com.tencent.bkrepo.ddc.utils.isObject
import com.tencent.bkrepo.ddc.utils.isString
import com.tencent.bkrepo.ddc.utils.writeBool
import com.tencent.bkrepo.ddc.utils.writeInteger
import com.tencent.bkrepo.ddc.utils.writeString
import com.tencent.bkrepo.ddc.utils.writerObject
import java.nio.ByteBuffer

/**
 * 批量操作
 */
data class BatchOps(
    val ops: List<BatchOp>
) {

    fun serialize(): CbObject {
        return CbObject.build { writer ->
            writer.beginUniformArray(BatchOps::ops.name, CbFieldType.Object)
            ops.forEach { op ->
                writer.beginObject()
                writer.writeInteger(BatchOp::opId.name, op.opId)
                writer.writeString(BatchOp::bucket.name, op.bucket)
                writer.writeString(BatchOp::key.name, op.key)
                writer.writeString(BatchOp::op.name, op.op)
                writer.writeBool(BatchOp::resolveAttachments.name, op.resolveAttachments)
                op.payload?.let { writer.writerObject(BatchOp::payload.name, it) }
                op.payloadHash?.let { writer.writeString(BatchOp::payloadHash.name, it) }
                writer.endObject()
            }
            writer.endArray()
        }
    }

    companion object {
        fun deserialize(byteArray: ByteArray): BatchOps {
            val opsCbArray = CbObject(ByteBuffer.wrap(byteArray))[BatchOps::ops.name].asArray()
            if (opsCbArray == CbArray.EMPTY) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "ops is empty")
            }
            val ops = opsCbArray.map { deserializeBatchOp(BatchOp(), it.asObject()) }
            return BatchOps(ops)
        }

        /**
         * 仅支持反序列化最外层的属性，且只支持了BatchOp的字段类型
         */
        private fun deserializeBatchOp(obj: BatchOp, cbObject: CbObject): BatchOp {
            fun valOf(field: CbField) = when {
                field.isBool() -> field.asBool() as Any
                field.isString() -> field.asString()
                field.isInteger() -> field.asUInt32()
                field.isObject() -> field.asObject()
                else -> throw RuntimeException("unsupported field type ${field.getType()}")
            }
            cbObject.forEach { cbField ->
                val field = obj.javaClass.getDeclaredField(cbField.name)
                field.isAccessible = true
                field.set(obj, valOf(cbField))
            }
            return obj
        }
    }
}

/**
 * 操作
 */
data class BatchOp(
    val opId: Int = 0,
    val bucket: String = "",
    val key: String = "",
    val op: String = Operation.INVALID.toString(),
    /**
     * 是否检查ref引用的所有blob是否存在
     */
    val resolveAttachments: Boolean = false,
    /**
     * ref inline blob，op为PUT时有值
     */
    val payload: CbObject? = null,
    /**
     * ref inline blob hash, op为PUT时有值
     */
    val payloadHash: String? = null,
)

/**
 * 操作类型
 */
enum class Operation {
    INVALID,
    GET,
    PUT,
    HEAD;
}
