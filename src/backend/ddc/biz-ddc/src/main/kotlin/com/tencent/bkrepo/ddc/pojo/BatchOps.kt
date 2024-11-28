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

import com.tencent.bkrepo.ddc.serialization.CbField
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.utils.isBool
import com.tencent.bkrepo.ddc.utils.isInteger
import com.tencent.bkrepo.ddc.utils.isObject
import com.tencent.bkrepo.ddc.utils.isString
import java.nio.ByteBuffer

/**
 * 批量操作
 */
data class BatchOps(
    val ops: List<BatchOp>
) {
    companion object {
        fun deserialize(byteArray: ByteArray): BatchOps {
            val ops = CbObject(ByteBuffer.wrap(byteArray))[BatchOps::ops.name].asArray().map {
                deserializeBatchOp(BatchOp(), it.asObject())
            }
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
            cbObject.forEach { field ->
                obj.javaClass.getDeclaredField(field.name).set(obj, valOf(field))
            }
            return obj
        }
    }
}

/**
 * 操作
 */
data class BatchOp(
    var opId: Int = 0,
    var bucket: String = "",
    var key: String = "",
    var op: String = Operation.INVALID.toString(),
    /**
     * 是否检查ref引用的所有blob是否存在
     */
    var resolveAttachments: Boolean = false,
    /**
     * ref inline blob，op为PUT时有值
     */
    var payload: CbObject? = null,
    /**
     * ref inline blob hash, op为PUT时有值
     */
    var payloadHash: String? = null,
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
