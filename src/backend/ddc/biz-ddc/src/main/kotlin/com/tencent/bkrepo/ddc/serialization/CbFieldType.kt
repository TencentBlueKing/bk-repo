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

/**
 * 通过CompactBinary序列化后的字段类型
 */
enum class CbFieldType(val value: Byte) {
    None(0x00),
    Null(0x01),

    /**
     * 不带类型的Object，子字段需要写入类型信息
     */
    Object(0x02),

    /**
     * 带类型的Object，所有子字段使用同一种类型
     */
    UniformObject(0x03),

    /**
     * 不带类型的数组，子字段需要写入类型信息
     */
    Array(0x04),

    /**
     * 带类型的Array，所有子字段使用同一种类型
     */
    UniformArray(0x05),
    Binary(0x06),
    String(0x07),
    IntegerPositive(0x08),
    IntegerNegative(0x09),
    Float32(0x0a),
    Float64(0x0b),
    BoolFalse(0x0c),
    BoolTrue(0x0d),
    ObjectAttachment(0x0e),

    /**
     * BinaryAttachment is a reference to a binary attachment stored externally.
     *
     * Payload is a 160-bit hash digest of the referenced binary data.
     */
    BinaryAttachment(0x0f),

    /**
     * Hash. Payload is a 160-bit hash digest.
     */
    Hash(0x10),
    Uuid(0x11),
    /**
     *  Date and time between 0001-01-01 00:00:00.0000000 and 9999-12-31 23:59:59.9999999.
     *
     *  Payload is a big endian int64 count of 100ns ticks since 0001-01-01 00:00:00.0000000.
     */
    DateTime(0x12),
    TimeSpan(0x13),
    ObjectId(0x14),
    CustomById(0x1e),
    CustomByName(0x1f),
    HasFieldType(0x40),
    HasFieldName(0x80.toByte());

    companion object {
        const val SIZE_OF_CB_FIELD_TYPE = 1
        fun getByValue(value: Byte): CbFieldType? {
            return if (value >= None.value && value <= Object.value) {
                CbFieldType.values()[value.toInt()]
            } else {
                CbFieldType.values().firstOrNull { it.value == value }
            }
        }
    }
}
