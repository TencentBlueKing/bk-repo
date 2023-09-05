/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.ddc.utils

import com.tencent.bkrepo.ddc.serialization.CbFieldType
import com.tencent.bkrepo.ddc.serialization.CbWriterBase
import com.tencent.bkrepo.ddc.serialization.VarULong
import com.tencent.bkrepo.ddc.utils.BlakeUtils.OUT_LEN
import java.nio.ByteBuffer

fun CbWriterBase.measureFieldWithLength(length: Int) = length + VarULong.measureUnsigned(length.toLong());

fun CbWriterBase.writeFieldWithLength(type: CbFieldType, name: String?, length: Int): ByteBuffer {
    val fullLength = measureFieldWithLength(length)
    val buffer = writeField(type, name, fullLength)
    VarULong.writeUnsigned(buffer, length.toLong())
    return buffer
}

fun CbWriterBase.beginUniformArray(elementType: CbFieldType) = beginUniformArray(null, elementType);

fun CbWriterBase.beginUniformArray(name: String? = null, elementType: CbFieldType) = beginArray(name, elementType);

fun CbWriterBase.writeNull(name: String? = null) = writeField(CbFieldType.Null, name, 0)

fun CbWriterBase.writeNullValue() = writeField(CbFieldType.Null, null, 0)

fun CbWriterBase.writeBoolValue(value: Boolean) = writeBool(null, value)

fun CbWriterBase.writeBool(name: String? = null, value: Boolean) =
    writeField(if (value) CbFieldType.BoolTrue else CbFieldType.BoolFalse, name, 0)

fun CbWriterBase.writeIntegerValue(value: Int) = writeLong(null, value.toLong())
fun CbWriterBase.writeInteger(name: String, value: Int) = writeLong(name, value.toLong())

fun CbWriterBase.writeLongValue(value: Long) = writeLong(null, value)

fun CbWriterBase.writeLong(name: String? = null, value: Long) {
    if (value >= 0) {
        val length = VarULong.measureUnsigned(value)
        val data = writeField(CbFieldType.IntegerPositive, name, length)
        VarULong.writeUnsigned(data, value)
    } else {
        // TODO 直接取反可能存在溢出的问题
        val length = VarULong.measureUnsigned(-value)
        val data = writeField(CbFieldType.IntegerNegative, name, length)
        VarULong.writeUnsigned(data, -value)
    }
}

fun CbWriterBase.writeDoubleValue(value: Double) = writeDouble(null, value)

fun CbWriterBase.writeDouble(name: String? = null, value: Double) {
    val buffer = writeField(CbFieldType.Float64, name, Double.SIZE_BYTES)
    buffer.putDouble(value)
}

fun CbWriterBase.writeHashValue(value: ByteArray) = writeHash(null, value)

fun CbWriterBase.writeHash(name: String? = null, value: ByteArray) {
    val buffer = writeField(CbFieldType.Hash, name, OUT_LEN)
    buffer.put(value)
}

fun CbWriterBase.writeBinaryAttachmentValue(hash: ByteArray) = writeBinaryAttachment(null, hash)

fun CbWriterBase.writeBinaryAttachment(name: String? = null, hash: ByteArray) {
    val buffer = writeField(CbFieldType.BinaryAttachment, name, OUT_LEN)
    buffer.put(hash)
}

fun CbWriterBase.writeObjectAttachmentValue(hash: ByteArray) = writeObjectAttachment(null, hash)

fun CbWriterBase.writeObjectAttachment(name: String? = null, hash: ByteArray) {
    writeField(CbFieldType.ObjectAttachment, name, OUT_LEN).put(hash)
}

fun CbWriterBase.writeStringValue(value: String) = writeString(null, value)

fun CbWriterBase.writeString(name: String? = null, value: String?) {
    if (value != null) {
        writeFieldWithLength(CbFieldType.String, name, value.length).put(value.toByteArray())
    }
}

fun CbWriterBase.writeBinaryReference(name: String? = null, data: ByteBuffer) {
    val length = VarULong.measureUnsigned(data.remaining().toLong())
    val buffer = writeField(CbFieldType.Binary, name, length)
    VarULong.writeUnsigned(buffer, data.remaining().toLong())
    writeReference(data.asReadOnlyBuffer())
}

fun CbWriterBase.writeBinaryValue(value: ByteBuffer) = writeBinary(null, value)

fun CbWriterBase.writeBinary(name: String? = null, value: ByteBuffer) {
    writeFieldWithLength(CbFieldType.Binary, name, value.remaining()).put(value.asReadOnlyBuffer())
}

fun CbWriterBase.writeBinaryArrayValue(value: ByteArray) = writeBinaryValue(ByteBuffer.wrap(value))

fun CbWriterBase.writeBinaryArray(name: String, value: ByteArray) = writeBinary(name, ByteBuffer.wrap(value))
