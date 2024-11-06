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

package com.tencent.bkrepo.ddc.serialization

import com.tencent.bkrepo.ddc.utils.ByteBufferUtils
import org.bouncycastle.crypto.digests.Blake3Digest
import java.nio.ByteBuffer

class CbArray(private val innerField: CbField) : Iterable<CbField> {
    val count: Int
        get() {
            val payload = innerField.payload.asReadOnlyBuffer()
            payload.position(VarULong.measure(payload))
            return VarULong.readUnsigned(payload).toInt()
        }

    constructor() : this(EMPTY.innerField)

    constructor(data: ByteBuffer, type: CbFieldType = CbFieldType.HasFieldType) : this(CbField(data, type))

    fun asField(): CbField = innerField

    /** Returns the size of the array in bytes if serialized by itself with no name. */
    fun getSize(): Int = minOf(innerField.getPayloadSize().toInt() + 1, Int.MAX_VALUE)

    /** Calculate the hash of the array if serialized by itself with no name. */
    fun getHash(): Blake3Hash {
        val digest = Blake3Digest(Blake3Hash.NUM_BYTES)

        val serializedType = byteArrayOf(innerField.getType().value)
        digest.update(serializedType, 0, serializedType.size)
        val payload = ByteBuffer.allocate(innerField.payload.remaining())
        payload.put(innerField.payload.asReadOnlyBuffer())
        digest.update(payload.array(), 0, payload.remaining())

        val result = ByteArray(Blake3Hash.NUM_BYTES)
        digest.doFinal(result, 0)
        return Blake3Hash(ByteBuffer.wrap(result))
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CbArray) return false
        return innerField.getType() == other.innerField.getType() && innerField.payload == other.innerField.payload
    }

    override fun hashCode(): Int = getHash().getBytes().getInt()


    /** Copy the array into a buffer of exactly getSize() bytes, with no name. */
    fun copyTo(buffer: ByteBuffer) {

        buffer.put(innerField.getType().value)
        buffer.put(innerField.payload.asReadOnlyBuffer())
    }

    fun iterateAttachments(visitor: (CbField) -> Unit) = createIterator().iterateRangeAttachments(visitor)

    /**
     * Try to get a view of the array as it would be serialized, such as by CopyTo.
     *
     * A view is available if the array contains its type and has no name. Access the equivalent
     * for other arrays through FCbArray::GetBuffer, FCbArray::Clone, or CopyTo.
     */
    fun tryGetView(): Pair<Boolean, ByteBuffer> =
        if (CbFieldUtils.hasFieldName(innerField.typeWithFlags)) {
            Pair(false, ByteBufferUtils.EMPTY)
        } else {
            innerField.tryGetView()
        }

    override fun iterator(): Iterator<CbField> = innerField.iterator()

    fun createIterator(): CbFieldIterator = innerField.createIterator()

    companion object {
        val EMPTY: CbArray = CbArray(CbField(ByteBuffer.wrap(byteArrayOf(CbFieldType.Array.value, 1, 0))))
        fun fromFieldNoCheck(field: CbField): CbArray = CbArray(field)
    }
}
