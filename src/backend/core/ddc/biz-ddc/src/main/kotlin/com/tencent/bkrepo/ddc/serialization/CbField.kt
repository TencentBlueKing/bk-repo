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
import com.tencent.bkrepo.ddc.serialization.CbFieldType.Companion.SIZE_OF_CB_FIELD_TYPE
import com.tencent.bkrepo.ddc.serialization.CbFieldUtils.getType
import com.tencent.bkrepo.ddc.utils.ByteBufferUtils
import org.bouncycastle.crypto.digests.Blake3Digest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.experimental.and
import kotlin.experimental.or


class CbField : Iterable<CbField> {
    val typeWithFlags: Byte
    val fieldData: ByteBuffer
    val payload: ByteBuffer
    val nameLen: Int
    private val payloadOffset: Int
    val name: String

    val value: Any?
        get() {
            val fieldType = getType()
            return when (fieldType) {
                CbFieldType.None -> NONE
                CbFieldType.Null -> null
                CbFieldType.Object, CbFieldType.UniformObject -> asObject()
                CbFieldType.Array, CbFieldType.UniformArray -> asArray()
                CbFieldType.Binary -> asBinary()
                CbFieldType.String -> asString()
                CbFieldType.IntegerPositive -> asUInt64()
                CbFieldType.IntegerNegative -> asInt64()
                CbFieldType.Float32 -> asFloat()
                CbFieldType.Float64 -> asDouble()
                CbFieldType.BoolFalse -> false
                CbFieldType.BoolTrue -> true
                CbFieldType.ObjectAttachment -> asObjectAttachment()
                CbFieldType.BinaryAttachment -> asBinaryAttachment()
                CbFieldType.Hash -> asHash()
                CbFieldType.Uuid -> asUuid()
//                CbFieldType.DateTime -> asDateTime()
//                CbFieldType.TimeSpan -> asTimeSpan()
                CbFieldType.ObjectId -> asObjectId()
                else -> throw NotImplementedException("Unknown field type ($fieldType)")
            }
        }
    var error: CbFieldError = CbFieldError.None
        private set

    constructor() : this(ByteBufferUtils.EMPTY, CbFieldType.None)

    constructor(other: CbField) {
        typeWithFlags = other.typeWithFlags
        fieldData = other.fieldData.asReadOnlyBuffer()
        payload = other.payload.asReadOnlyBuffer()
        nameLen = other.nameLen
        name = other.name
        payloadOffset = other.payloadOffset
        error = other.error
    }

    constructor(data: ByteBuffer, type: CbFieldType = CbFieldType.HasFieldType) : this(data, type.value)

    constructor(data: ByteBuffer, type: Byte) {
        var offset = 0
        var fieldTypeValue = type
        if (CbFieldUtils.hasFieldType(fieldTypeValue)) {
            fieldTypeValue = data.get() or CbFieldType.HasFieldType.value
            offset++
        }
        if (CbFieldUtils.hasFieldName(fieldTypeValue)) {
            nameLen = VarULong.readUnsigned(data).toInt()
            val nameArr = ByteArray(nameLen)
            for (i in data.position() until data.position() + nameLen) {
                nameArr[i - data.position()] = data.get(i)
            }
            name = String(nameArr)
            offset = data.position() + nameLen
        } else {
            nameLen = 0
            name = ""
        }

        typeWithFlags = fieldTypeValue
        payloadOffset = offset

        data.position(payloadOffset)
        val payloadSize = getPayloadSize(data.slice())
        val limit = minOf(data.limit().toLong(), payloadOffset.toLong() + payloadSize).toInt()
        data.limit(limit)
        payload = data.slice().asReadOnlyBuffer()

        data.position(0)
        fieldData = data.slice().asReadOnlyBuffer()
    }

    fun asObject(): CbObject {
        return if (CbFieldUtils.isObject(typeWithFlags)) {
            error = CbFieldError.None
            CbObject.fromFieldNoCheck(this)
        } else {
            error = CbFieldError.TypeError
            CbObject.EMPTY
        }
    }

    fun asArray(): CbArray {
        return if (CbFieldUtils.isArray(typeWithFlags)) {
            error = CbFieldError.None
            CbArray.fromFieldNoCheck(this)
        } else {
            error = CbFieldError.TypeError
            CbArray.EMPTY
        }
    }

    fun asBinary(): ByteBuffer {
        return asBinary(ByteBufferUtils.EMPTY)
    }

    fun asBinary(defaultValue: ByteBuffer): ByteBuffer {
        return if (CbFieldUtils.isBinary(typeWithFlags)) {
            error = CbFieldError.None

            val buffer = payload.asReadOnlyBuffer()
            val length = VarULong.readUnsigned(buffer)
            buffer.limit(buffer.position() + length.toInt())
            return buffer.slice()
        } else {
            error = CbFieldError.TypeError
            defaultValue
        }
    }

    fun asBinaryArray(): ByteArray {
        return asBinaryArray(byteArrayOf())
    }

    fun asBinaryArray(defaultValue: ByteArray): ByteArray {
        val buffer = asBinary(ByteBuffer.wrap(defaultValue))
        return buffer.array().sliceArray(0 until buffer.remaining())
    }

    fun asString(defaultValue: String = ""): String {
        return if (CbFieldUtils.isString(typeWithFlags)) {
            val buffer = payload.asReadOnlyBuffer()
            val valueSize = VarULong.readUnsigned(buffer)
            if (valueSize >= (1L shl 31)) {
                error = CbFieldError.RangeError
                defaultValue
            } else {
                error = CbFieldError.None
                val arr = ByteBuffer.allocate(valueSize.toInt())
                arr.put(buffer)
                String(arr.array())
            }
        } else {
            error = CbFieldError.TypeError
            defaultValue;
        }
    }

    fun asInt8(defaultValue: Byte = 0): Byte {
        return asInteger(defaultValue.toLong(), 7, true).toByte()
    }

    fun asInt16(defaultValue: Short = 0): Short {
        return asInteger(defaultValue.toLong(), 15, true).toShort()
    }

    fun asInt32(defaultValue: Int = 0): Int {
        return asInteger(defaultValue.toLong(), 31, true).toInt()
    }

    fun asInt64(defaultValue: Long = 0): Long {
        return asInteger(defaultValue, 63, true)
    }

    fun asUInt8(defaultValue: Byte = 0): Byte {
        return asInteger(defaultValue.toLong(), 8, false).toByte()
    }

    fun asUInt16(defaultValue: Short = 0): Short {
        return asInteger(defaultValue.toLong(), 16, false).toShort()
    }

    fun asUInt32(defaultValue: Int = 0): Int {
        return asInteger(defaultValue.toLong(), 32, false).toInt()
    }

    fun asUInt64(defaultValue: Long = 0): Long {
        return asInteger(defaultValue, 64, false)
    }

    private fun asInteger(defaultValue: Long, magnitudeBits: Int, isSigned: Boolean): Long {
        if (CbFieldUtils.isInteger(typeWithFlags)) {
            // 用于判断是否溢出，下面以16位整型为例，几个outOfRangeMask值的示例
            // 读取无符号整型，magnitudeBits为16, outOfRangeMask值为1111_1111 1111_1111 0000_0000 0000_0000
            // 读取有符号整型，magnitudeBits为15, outOfRangeMask值为1111_1111 1111_1111 1000_0000 0000_0000
            val outOfRangeMask = -2L shl (magnitudeBits - 1)
            // isNegative为1表示负数，0表示正数
            val isNegative = typeWithFlags and 1.toByte()


            // isNegative表示正数时，value与magnitude相同， 1111_1110
            // isNegative表示负数时，0000_0001 1111_1111 1111_1111
            val magnitude = VarULong.readUnsigned(payload.asReadOnlyBuffer())
            // TODO 正数是原来的值，负数则直接按位取反，负数的情况下读出来的值与写入的值不一致，应该再加1才是负数的补码表示？
            val value = magnitude xor -isNegative.toLong()

            // 为负数时必须是有符号整型
            return if ((magnitude and outOfRangeMask) == 0L && (isNegative == 0.toByte() || isSigned)) {
                error = CbFieldError.None
                value
            } else {
                error = CbFieldError.RangeError
                defaultValue
            }
        } else {
            error = CbFieldError.TypeError
            return defaultValue
        }
    }

    /**
     * TODO 确认转换结果精度
     */
    fun asFloat(defaultValue: Float = 0.0f): Float {
        return when (getType()) {
            CbFieldType.IntegerPositive, CbFieldType.IntegerNegative -> {
                val isNegative = typeWithFlags and 1.toByte()
                val outOfRangeMask: Long = ((1L shl FLT_MANT_DIG) - 1).inv()

                val magnitude = VarULong.readUnsigned(payload.asReadOnlyBuffer()) + isNegative
                val isInRange = (magnitude and outOfRangeMask) == 0L

                error = if (isInRange) CbFieldError.None else CbFieldError.RangeError
                if (isInRange) {
                    if (isNegative != 0.toByte()) {
                        (-magnitude).toFloat()
                    } else {
                        magnitude.toFloat()
                    }
                } else {
                    defaultValue
                }
            }

            CbFieldType.Float32 -> {
                error = CbFieldError.None
                payload.asReadOnlyBuffer().float
            }

            CbFieldType.Float64 -> {
                error = CbFieldError.RangeError
                defaultValue
            }

            else -> {
                error = CbFieldError.TypeError
                defaultValue
            }
        }
    }

    fun asDouble(): Double = asDouble(0.0)

    /**
     * TODO 确认转换结果精度
     */
    fun asDouble(defaultValue: Double): Double {
        when (getType()) {
            CbFieldType.IntegerPositive, CbFieldType.IntegerNegative -> {
                val isNegative = typeWithFlags and 1.toByte()
                val outOfRangeMask = ((1L shl DBL_MANT_DIG) - 1).inv()

                val magnitude = VarULong.readUnsigned(payload.asReadOnlyBuffer()) + isNegative
                val isInRange = (magnitude and outOfRangeMask) == 0L
                error = if (isInRange) CbFieldError.None else CbFieldError.RangeError
                return if (isInRange) {
                    if (isNegative != 0.toByte()) -magnitude.toDouble() else magnitude.toDouble()
                } else {
                    defaultValue
                }
            }

            CbFieldType.Float32 -> {
                error = CbFieldError.None
                return payload.asReadOnlyBuffer().getFloat().toDouble()
            }

            CbFieldType.Float64 -> {
                error = CbFieldError.None
                return payload.asReadOnlyBuffer().getDouble()
            }

            else -> {
                error = CbFieldError.TypeError
                return defaultValue
            }
        }
    }

    fun asBool(): Boolean = asBool(false)

    fun asBool(defaultValue: Boolean): Boolean {
        return when (getType()) {
            CbFieldType.BoolTrue -> {
                error = CbFieldError.None
                true
            }

            CbFieldType.BoolFalse -> {
                error = CbFieldError.None
                false
            }

            else -> {
                error = CbFieldError.TypeError
                defaultValue
            }
        }
    }

    fun asObjectAttachment(): CbObjectAttachment = asObjectAttachment(CbObjectAttachment.ZERO)

    fun asObjectAttachment(defaultValue: CbObjectAttachment): CbObjectAttachment {
        return if (CbFieldUtils.isObjectAttachment(typeWithFlags)) {
            error = CbFieldError.None
            CbObjectAttachment(IoHash(payload.asReadOnlyBuffer()))
        } else {
            error = CbFieldError.TypeError
            defaultValue
        }
    }

    fun asBinaryAttachment(): CbBinaryAttachment = asBinaryAttachment(CbBinaryAttachment.ZERO)

    fun asBinaryAttachment(defaultValue: CbBinaryAttachment): CbBinaryAttachment {
        return if (CbFieldUtils.isBinaryAttachment(typeWithFlags)) {
            error = CbFieldError.None
            CbBinaryAttachment(IoHash(payload.asReadOnlyBuffer()))
        } else {
            error = CbFieldError.TypeError
            defaultValue
        }
    }

    fun asAttachment(): IoHash {
        return asAttachment(IoHash.ZERO)
    }

    fun asAttachment(defaultValue: IoHash): IoHash {
        return if (CbFieldUtils.isAttachment(typeWithFlags)) {
            error = CbFieldError.None
            IoHash(payload.asReadOnlyBuffer())
        } else {
            error = CbFieldError.TypeError
            defaultValue
        }
    }

    fun asHash(): IoHash {
        return asHash(IoHash.ZERO)
    }

    fun asHash(defaultValue: IoHash): IoHash {
        return if (CbFieldUtils.isHash(typeWithFlags)) {
            error = CbFieldError.None
            IoHash(payload.asReadOnlyBuffer())
        } else {
            error = CbFieldError.TypeError
            defaultValue
        }
    }

    /**
     * TODO 与C# 的GUID转换对比
     */
    fun asUuid(defaultValue: UUID = UUID(0, 0)): UUID {
        return if (CbFieldUtils.isUuid(typeWithFlags)) {
            error = CbFieldError.None
            val source = payload.asReadOnlyBuffer()
            val target = ByteBuffer.allocate(16)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(source.getInt())
                .putShort(source.getShort())
                .putShort(source.getShort())
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(source.getLong())
            target.flip()
            return UUID(target.getLong(), target.getLong())
        } else {
            error = CbFieldError.TypeError
            defaultValue
        }
    }

    fun asDateTimeTicks(defaultValue: Long = 0L): Long {
        return if (CbFieldUtils.isDateTime(typeWithFlags)) {
            error = CbFieldError.None
            payload.asReadOnlyBuffer().getLong()
        } else {
            error = CbFieldError.TypeError
            defaultValue
        }
    }

    fun asTimeSpanTicks(defaultValue: Long = 0L): Long {
        return if (CbFieldUtils.isTimeSpan(typeWithFlags)) {
            error = CbFieldError.None
            payload.asReadOnlyBuffer().getLong()
        } else {
            error = CbFieldError.TypeError
            defaultValue
        }
    }

    fun asObjectId(defaultValue: ByteBuffer = ByteBufferUtils.EMPTY): ByteBuffer {
        return if (CbFieldUtils.isObjectId(typeWithFlags)) {
            error = CbFieldError.None
            payload.asReadOnlyBuffer()
        } else {
            error = CbFieldError.TypeError
            defaultValue
        }
    }

    fun hasValue() = !CbFieldUtils.isNone(typeWithFlags);
    fun hasError() = error != CbFieldError.None;

    /**
     * type + name + payload
     */
    fun getSize() = SIZE_OF_CB_FIELD_TYPE + getViewNoType().remaining()

    /**
     * 获取field hash， 32 bytes
     */
    fun getHash(): Blake3Hash {
        val digest = Blake3Digest(Blake3Hash.NUM_BYTES)

        val data = ByteArray(1) { CbFieldUtils.getSerializedType(typeWithFlags) }
        digest.update(data, 0, data.size)

        val viewNoType = getViewNoType()
        val buffer = ByteArray(viewNoType.remaining())
        viewNoType.get(buffer)
        digest.update(buffer, 0, buffer.size)

        val hash = ByteArray(Blake3Hash.NUM_BYTES)
        digest.doFinal(hash, 0)

        return Blake3Hash(ByteBuffer.wrap(hash))
    }

    override fun hashCode(): Int {
        throw NotImplementedException()
    }

    override fun equals(other: Any?): Boolean {
        return other is CbField &&
                CbFieldUtils.getSerializedType(typeWithFlags) == CbFieldUtils.getSerializedType(other.typeWithFlags) &&
                getViewNoType() == other.getViewNoType()
    }

    /**
     * buffer 大小与[getSize]返回值相同
     */
    fun copyTo(buffer: ByteBuffer) {
        buffer.put(CbFieldUtils.getSerializedType(typeWithFlags))
        buffer.put(getViewNoType())
    }

    fun iterateAttachments(visitor: (CbField) -> Unit) {
        when (getType()) {
            CbFieldType.Object, CbFieldType.UniformObject -> CbObject.fromFieldNoCheck(this).iterateAttachments(visitor)
            CbFieldType.Array, CbFieldType.UniformArray -> CbArray.fromFieldNoCheck(this).iterateAttachments(visitor)
            CbFieldType.ObjectAttachment, CbFieldType.BinaryAttachment -> visitor(this)
            else -> {}
        }
    }

    fun tryGetView(): Pair<Boolean, ByteBuffer> {
        return if (CbFieldUtils.hasFieldType(typeWithFlags)) {
            Pair(true, fieldData.asReadOnlyBuffer())
        } else {
            Pair(false, ByteBufferUtils.EMPTY)
        }
    }

    operator fun get(name: String): CbField = firstOrNull { it.name == name } ?: EMPTY

    fun createIterator(): CbFieldIterator {
        val localTypeWithFlags = typeWithFlags
        if (CbFieldUtils.hasFields(localTypeWithFlags)) {
            val payloadBytes = payload.asReadOnlyBuffer()
            val payloadSize = VarULong.readUnsigned(payloadBytes)

            val numByteCount = if (CbFieldUtils.isArray(localTypeWithFlags)) VarULong.measure(payloadBytes) else 0
            if (payloadSize > numByteCount) {
                payloadBytes.position(payloadBytes.position() + numByteCount)
                var uniformType = CbFieldType.HasFieldType.value
                if (CbFieldUtils.hasUniformFields(typeWithFlags)) {
                    uniformType = payloadBytes.get()
                }
                return CbFieldIterator(payloadBytes.slice(), uniformType)
            }
        }
        return CbFieldIterator(ByteBufferUtils.EMPTY, CbFieldType.HasFieldType.value)
    }

    override fun iterator(): Iterator<CbField> {
        return createIterator()
    }

    private fun getViewNoType(): ByteBuffer {
        val nameSize = if (CbFieldUtils.hasFieldName(typeWithFlags)) {
            nameLen + VarULong.measureUnsigned(nameLen.toLong())
        } else {
            0
        }

        val buffer = fieldData.asReadOnlyBuffer()
        buffer.position(payloadOffset - nameSize)
        return buffer.slice()
    }

    fun getType(): CbFieldType = getType(typeWithFlags)

    fun getPayloadSize(payload: ByteBuffer = this.payload): Long {
        return when (getType()) {
            CbFieldType.None, CbFieldType.Null -> 0L
            CbFieldType.Object,
            CbFieldType.UniformObject,
            CbFieldType.Array,
            CbFieldType.UniformArray,
            CbFieldType.Binary,
            CbFieldType.String -> {
                val buffer = payload.asReadOnlyBuffer()
                val payloadSize = VarULong.readUnsigned(buffer)
                buffer.position() + payloadSize
            }

            CbFieldType.IntegerPositive, CbFieldType.IntegerNegative -> {
                VarULong.measure(payload).toLong()
            }

            CbFieldType.Float32 -> 4L
            CbFieldType.Float64 -> 8L
            CbFieldType.BoolFalse, CbFieldType.BoolTrue -> 0L
            CbFieldType.ObjectAttachment, CbFieldType.BinaryAttachment, CbFieldType.Hash -> 20L
            CbFieldType.Uuid -> 16L
            CbFieldType.DateTime, CbFieldType.TimeSpan -> 8L
            CbFieldType.ObjectId -> 12L
            else -> 0L
        }
    }

    enum class CbFieldError(val value: Byte) {
        /**
         * 未出错
         */
        None(0x01),

        /**
         * 类型错误
         */
        TypeError(0x02),

        /**
         * 请求数据范围错误
         */
        RangeError(0x03),
    }

    class NoneValueType

    companion object {
        private const val FLT_MANT_DIG = 24
        private const val DBL_MANT_DIG = 53
        val NONE: NoneValueType = NoneValueType()
        val EMPTY: CbField = CbField()
    }
}
