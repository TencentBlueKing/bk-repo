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

package com.tencent.bkrepo.ddc.serialization;

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.io.SegmentedStringWriter
import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.ddc.exception.NotImplementedException
import com.tencent.bkrepo.ddc.serialization.CbFieldType.Companion.SIZE_OF_CB_FIELD_TYPE
import com.tencent.bkrepo.ddc.utils.BlakeUtils.hex
import com.tencent.bkrepo.ddc.utils.ByteBufferUtils
import org.bouncycastle.crypto.digests.Blake3Digest
import java.nio.ByteBuffer

class CbObject : Iterable<CbField> {

    val innerField: CbField

    constructor(field: CbField) {
        innerField = CbField(field.fieldData.asReadOnlyBuffer(), field.typeWithFlags)
    }

    constructor(buffer: ByteBuffer, fieldType: CbFieldType = CbFieldType.HasFieldType) {
        innerField = CbField(buffer.asReadOnlyBuffer(), fieldType)
    }

    fun find(name: String): CbField {
        return innerField[name]
    }

    fun findIgnoreCase(name: String): CbField {
        return innerField.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CbField.EMPTY
    }

    operator fun get(name: String): CbField = innerField[name]

    fun asField() = innerField

    /**
     * Returns the size of the object in bytes if serialized by itself with no name.
     */
    fun getSize(): Int {
        return SIZE_OF_CB_FIELD_TYPE + innerField.payload.remaining()
    }

    /**
     * Calculate the hash of the object if serialized by itself with no name.
     */
    fun getHash(): Blake3Hash {
        val digest = Blake3Digest()

        val temp = byteArrayOf(innerField.getType().value)
        digest.update(temp, 0, temp.size)
        val payload = ByteBuffer.allocate(innerField.payload.remaining())
        payload.put(innerField.payload.asReadOnlyBuffer())
        digest.update(payload.array(), 0, payload.remaining())

        val data = ByteArray(Blake3Hash.NUM_BYTES)
        digest.doFinal(data, 0)
        return Blake3Hash(ByteBuffer.wrap(data))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CbObject) return false
        return innerField.getType() == other.innerField.getType() && innerField.payload == other.innerField.payload
    }

    override fun hashCode(): Int {
        return getHash().getBytes().getInt()
    }

    fun copyTo(buffer: ByteBuffer) {
        buffer.put(innerField.getType().value)
        buffer.put(innerField.payload.asReadOnlyBuffer())
    }

    fun iterateAttachments(visitor: (CbField) -> Unit) {
        createIterator().iterateRangeAttachments(visitor)
    }

    fun getView(): ByteBuffer {
        val (result, view) = tryGetView()
        if (!result) {
            val data = ByteBuffer.allocate(getSize())
            copyTo(data)
            return data
        }
        return view
    }

    fun tryGetView(): Pair<Boolean, ByteBuffer> {
        if (CbFieldUtils.hasFieldName(innerField.typeWithFlags)) {
            return Pair(false, ByteBufferUtils.EMPTY)
        }
        return innerField.tryGetView()
    }

    fun createIterator() = innerField.createIterator()

    override fun iterator(): Iterator<CbField> {
        return innerField.iterator()
    }

    fun toJson(mapper: ObjectMapper): String {
        val factory = mapper.factory
        val sw = SegmentedStringWriter(factory._getBufferRecycler())
        mapper.factory.createGenerator(sw).use { g ->
            g.writeStartObject()
            for (field in innerField) {
                writeField(field, g)
            }
            g.writeEndObject()
        }

        return sw.getAndClear()
    }

    private fun writeField(field: CbField, writer: JsonGenerator) {
        if (CbFieldUtils.isObject(field.typeWithFlags)) {
            if (field.nameLen != 0) {
                writer.writeObjectFieldStart(field.name)
            } else {
                writer.writeStartObject()
            }
            val obj = field.asObject()
            for (objField in obj.innerField) {
                writeField(objField, writer)
            }
            writer.writeEndObject()
        } else if (CbFieldUtils.isArray(field.typeWithFlags)) {
            writer.writeArrayFieldStart(field.name)
            val array = field.asArray()
            for (objectField in array) {
                writeField(objectField, writer)
            }
            writer.writeEndArray()
        } else if (CbFieldUtils.isInteger(field.typeWithFlags)) {
            if (field.getType() == CbFieldType.IntegerNegative) {
                writer.writeNumberField(field.name, -field.asInt64())
            } else {
                writer.writeNumberField(field.name, field.asUInt64())
            }
        } else if (CbFieldUtils.isBool(field.typeWithFlags)) {
            writer.writeBooleanField(field.name, field.asBool())
        } else if (CbFieldUtils.isNull(field.typeWithFlags)) {
            writer.writeNull()
        } else if (CbFieldUtils.isDateTime(field.typeWithFlags)) {
            throw NotImplementedException()
//            writer.writeStringField(field.name, field.asDateTime())
        } else if (CbFieldUtils.isHash(field.typeWithFlags)) {
            writer.writeStringField(field.name, field.asHash().toString())
        } else if (CbFieldUtils.isString(field.typeWithFlags)) {
            writer.writeStringField(field.name, field.asString())
        } else if (CbFieldUtils.isObjectId(field.typeWithFlags)) {
            writer.writeStringField(field.name, field.asObjectId().hex())
        } else {
            throw NotImplementedException("Unhandled type ${field.getType()} when attempting to convert to json")
        }
    }

    companion object {
        val EMPTY = fromFieldNoCheck(CbField(ByteBuffer.wrap(byteArrayOf(CbFieldType.Object.value, 0))))

        fun fromFieldNoCheck(field: CbField): CbObject {
            return CbObject(field)
        }

        fun build(build: (writer: CbWriter) -> Unit): CbObject {
            val writer = CbWriter()
            writer.beginObject()
            build(writer)
            writer.endObject()
            return CbObject(ByteBuffer.wrap(writer.toByteArray()))
        }
    }
}
