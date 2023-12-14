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

import kotlin.experimental.and

object CbFieldUtils {
    private const val SERIALIZED_TYPE_MASK: Byte = 0b1001_1111.toByte()
    private const val TYPE_MASK: Byte = 0b0001_1111.toByte()

    private const val OBJECT_MASK: Byte = 0b0001_1110.toByte()
    private const val OBJECT_BASE: Byte = 0b0000_0010.toByte()

    private const val ARRAY_MASK: Byte = 0b0001_1110.toByte()
    private const val ARRAY_BASE: Byte = 0b0000_0100.toByte()

    private const val INTEGER_MASK: Byte = 0b0011_1110.toByte()
    private const val INTEGER_BASE: Byte = 0b0000_1000.toByte()

    private const val FLOAT_MASK: Byte = 0b0001_1100.toByte()
    private const val FLOAT_BASE: Byte = 0b0000_1000.toByte()

    private const val BOOL_MASK: Byte = 0b0001_1110.toByte()
    private const val BOOL_BASE: Byte = 0b0000_1100.toByte()

    private const val ATTACHMENT_MASK: Byte = 0b0001_1110.toByte()
    private const val ATTACHMENT_BASE: Byte = 0b0000_1110.toByte()

    fun getType(typeWithFlags: Byte): CbFieldType {
        return CbFieldType.getByValue(typeWithFlags and TYPE_MASK)!!
    }

    fun getSerializedType(typeWithFlags: Byte): Byte {
        return typeWithFlags and SERIALIZED_TYPE_MASK
    }

    fun hasFieldType(type: Byte): Boolean {
        return (type and CbFieldType.HasFieldType.value) != 0.toByte()
    }

    fun hasFieldName(type: Byte): Boolean {
        return (type and CbFieldType.HasFieldName.value) != 0.toByte()
    }

    fun isNone(type: Byte): Boolean {
        return getType(type) == CbFieldType.None
    }

    fun isNull(type: Byte): Boolean {
        return getType(type) == CbFieldType.Null
    }

    fun isObject(type: Byte): Boolean {
        return (type and OBJECT_MASK) == OBJECT_BASE
    }

    fun isArray(type: Byte): Boolean {
        return (type and ARRAY_MASK) == ARRAY_BASE
    }

    fun isBinary(type: Byte): Boolean {
        return getType(type) == CbFieldType.Binary
    }

    fun isString(type: Byte): Boolean {
        return getType(type) == CbFieldType.String
    }

    fun isInteger(type: Byte): Boolean {
        return (type and INTEGER_MASK) == INTEGER_BASE
    }

    fun isFloat(type: Byte): Boolean {
        return (type and FLOAT_MASK) == FLOAT_BASE
    }

    fun isBool(type: Byte): Boolean {
        return (type and BOOL_MASK) == BOOL_BASE
    }

    fun isObjectAttachment(type: Byte): Boolean {
        return getType(type) == CbFieldType.ObjectAttachment
    }

    fun isBinaryAttachment(type: Byte): Boolean {
        return getType(type) == CbFieldType.BinaryAttachment
    }

    fun isAttachment(type: Byte): Boolean {
        return (type and ATTACHMENT_MASK) == ATTACHMENT_BASE
    }

    fun isHash(type: Byte): Boolean {
        return getType(type) === CbFieldType.Hash || isAttachment(type)
    }

    fun isUuid(type: Byte): Boolean {
        return getType(type) === CbFieldType.Uuid
    }

    fun isDateTime(type: Byte): Boolean {
        return getType(type) === CbFieldType.DateTime
    }

    fun isTimeSpan(type: Byte): Boolean {
        return getType(type) === CbFieldType.TimeSpan
    }

    fun isObjectId(type: Byte): Boolean {
        return getType(type) === CbFieldType.ObjectId
    }

    fun hasFields(type: Byte): Boolean {
        val noFlags: CbFieldType = getType(type)
        return noFlags >= CbFieldType.Object && noFlags <= CbFieldType.UniformArray
    }

    fun hasUniformFields(type: Byte): Boolean {
        val localType: CbFieldType = getType(type)
        return localType === CbFieldType.UniformObject || localType === CbFieldType.UniformArray
    }

    fun mayContainAttachments(type: Byte): Boolean {
        return isObject(type) || isArray(type) || isAttachment(type)
    }
}
