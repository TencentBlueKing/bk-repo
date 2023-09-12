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

import com.tencent.bkrepo.ddc.exception.NotImplementedException
import com.tencent.bkrepo.ddc.utils.ByteBufferUtils
import java.nio.ByteBuffer

class CbFieldIterator : Iterator<CbField> {

    private var nextData: ByteBuffer
    private val uniformType: Byte
    var current: CbField = CbField.EMPTY
        private set

    constructor() : this(ByteBufferUtils.EMPTY, CbFieldType.HasFieldType.value)

    private constructor(field: CbField) {
        nextData = ByteBufferUtils.EMPTY
        current = field
        uniformType = CbFieldType.HasFieldType.value
    }

    constructor(data: ByteBuffer, uniformType: Byte) {
        this.nextData = data.asReadOnlyBuffer()
        this.uniformType = uniformType
        moveNext()
    }

    constructor(other: CbFieldIterator) {
        nextData = other.nextData.asReadOnlyBuffer()
        uniformType = other.uniformType
        current = other.current
    }

    fun isValid(): Boolean {
        return current.getType() != CbFieldType.None
    }

    fun iterateRangeAttachments(visitor: (CbField) -> Unit) {
        if (CbFieldUtils.hasFieldType(current.typeWithFlags)) {
            for (it in CbFieldIterator(this)) {
                if (CbFieldUtils.mayContainAttachments(it.typeWithFlags)) {
                    it.iterateAttachments(visitor)
                }
            }
        } else if (CbFieldUtils.mayContainAttachments(current.typeWithFlags)) {
            for (it in CbFieldIterator(this)) {
                it.iterateAttachments(visitor)
            }
        }
    }

    fun moveNext(): Boolean {
        return if (nextData.remaining() > 0) {
            current = CbField(nextData.asReadOnlyBuffer(), uniformType)
            nextData.position(current.fieldData.remaining())
            nextData = nextData.slice()
            true
        } else {
            current = CbField.EMPTY
            false
        }
    }

    override fun equals(other: Any?): Boolean {
        throw NotImplementedException()
    }

    override fun hashCode(): Int {
        throw NotImplementedException()
    }

    override fun hasNext(): Boolean {
        return isValid()
    }

    override fun next(): CbField {
        val field = current
        moveNext()
        return field
    }

    companion object {
        fun makeSingle(field: CbField): CbFieldIterator {
            return CbFieldIterator(field)
        }

        fun makeRange(view: ByteBuffer, type: Byte = CbFieldType.HasFieldType.value): CbFieldIterator {
            return CbFieldIterator(view, type)
        }
    }
}
