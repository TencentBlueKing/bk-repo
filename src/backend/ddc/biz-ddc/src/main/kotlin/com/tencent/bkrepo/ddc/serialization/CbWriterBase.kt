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

import com.tencent.bkrepo.ddc.exception.CbWriterException
import com.tencent.bkrepo.ddc.serialization.CbFieldType.Companion.SIZE_OF_CB_FIELD_TYPE
import com.tencent.bkrepo.ddc.utils.BlakeUtils
import com.tencent.bkrepo.ddc.utils.ByteBufferUtils
import java.nio.ByteBuffer
import java.util.ArrayDeque
import kotlin.experimental.or


abstract class CbWriterBase : ICbWriter {

    private val rootScope = Scope(fieldType = CbFieldType.Array)

    /**
     * 栈顶元素为当前正在操作的scope
     */
    private val openScopes = ArrayDeque<Scope>()
    private val freeScopes = ArrayDeque<Scope>()

    private var buffer = ByteBufferUtils.EMPTY

    /**
     * 当前正在操作的字段buffer起始位置，inclusive
     */
    private var bufferPos = 0

    /**
     * 当前正在操作的字段buffer结束位置，exclusive
     */
    private var bufferEnd = 0

    init {
        openScopes.push(rootScope)
    }

    protected fun reset() {
        addChildrenToFreeList(rootScope)
        rootScope.reset()
    }

    private fun addChildrenToFreeList(root: Scope) {
        var child = root.firstChild
        while (child != null) {
            addChildrenToFreeList(child)
            child.reset()
            freeScopes.push(child)
            child = child.nextSibling
        }
    }

    private fun allocScope(): Scope {
        return if (freeScopes.isNotEmpty()) {
            freeScopes.pop()
        } else {
            Scope()
        }
    }

    /**
     * 添加子字段数据
     */
    private fun addLeafData(data: ByteBuffer) {
        val scope = allocScope()
        scope.data = data.asReadOnlyBuffer()
        scope.length = data.remaining()

        val currentScope = openScopes.peek()
        currentScope.addChild(scope)
    }

    /**
     * 创建新字段
     */
    private fun enterScope(fieldType: CbFieldType, name: String?): Scope {
        val currentScope = openScopes.peek()

        val scope = allocScope()
        scope.fieldType = fieldType
        scope.writeFieldType = currentScope.uniformFieldType == CbFieldType.None
        scope.name = name

        currentScope.addChild(scope)

        openScopes.push(scope)
        return scope
    }

    /**
     * 结束字段创建，写入header数据到scope的data字段
     */
    private fun leaveScope() {
        writeFields()
        val scope = openScopes.peek()
        val childrenLength = scope.childrenLength()

        if (scope.fieldType != CbFieldType.None) {
            val typeAndNameLength = scope.typeAndNameLength()
            val payloadTypeAndItemCountLength = scope.payloadTypeAndItemCountLength()
            val payloadLength = payloadTypeAndItemCountLength.toLong() + childrenLength.toLong()
            val payloadLengthBytes = VarULong.measureUnsigned(payloadLength)

            val header = allocate(typeAndNameLength + payloadLengthBytes + payloadTypeAndItemCountLength)
            scope.data = header.asReadOnlyBuffer()
            bufferPos = bufferEnd

            if (scope.writeFieldType) {
                if (scope.name.isNullOrEmpty()) {
                    header.put(scope.fieldType.value)
                } else {
                    header.put(scope.fieldType.value or CbFieldType.HasFieldName.value)
                    VarULong.writeUnsigned(header, scope.name!!.length.toLong())
                    header.put(scope.name!!.toByteArray())
                }
            }
            VarULong.writeUnsigned(header, payloadLength)

            if (CbFieldUtils.isArray(scope.fieldType.value)) {
                VarULong.writeUnsigned(header, scope.itemCount.toLong())
            }

            if (scope.fieldType === CbFieldType.UniformObject || scope.fieldType === CbFieldType.UniformArray) {
                header.put(scope.uniformFieldType.value)
            }
        }

        scope.length = childrenLength + scope.data!!.remaining()
        openScopes.pop()
    }

    override fun beginObject(name: String?) {
        writeFields()

        val parentScope = openScopes.peek()
        parentScope.itemCount++

        enterScope(CbFieldType.Object, name)
    }

    override fun endObject() = leaveScope()

    override fun beginArray(name: String?, elementType: CbFieldType) {
        writeFields()

        val parentScope = openScopes.peek()
        parentScope.itemCount++

        val fieldType = if (elementType == CbFieldType.None) {
            CbFieldType.Array
        } else {
            CbFieldType.UniformArray
        }

        val scope = enterScope(fieldType, name)
        scope.uniformFieldType = elementType
    }

    override fun endArray() = leaveScope()

    private fun writeFields() {
        if (bufferPos < bufferEnd) {
            buffer.position(bufferPos)
            buffer.limit(bufferEnd)
            addLeafData(buffer.slice())
            bufferPos = bufferEnd
        }
    }

    private fun allocate(length: Int): ByteBuffer {
        if (bufferEnd + length > buffer.capacity()) {
            writeFields()
            buffer = allocateChunk(length)
            bufferPos = 0
            bufferEnd = 0
        }

        buffer.position(bufferEnd)
        buffer.limit(bufferEnd + length)
        val data = buffer.slice()
        bufferEnd += length

        return data
    }

    protected abstract fun allocateChunk(minSize: Int): ByteBuffer

    override fun writeField(type: CbFieldType, name: String?, length: Int): ByteBuffer {
        writeFieldHeader(type, name)
        val buffer = allocate(length)
        if (openScopes.count() == 1) {
            // TODO 确认是否需要改成所有情况都将数据刷到scope child
            writeFields()
        }
        return buffer
    }

    private fun writeFieldHeader(type: CbFieldType, name: String? = null) {
        val scope = openScopes.peek()
        if (name.isNullOrEmpty()) {
            val scopeType = scope.fieldType
            if (!CbFieldUtils.isArray(scopeType.value)) {
                throw CbWriterException("Anonymous fields are not allowed within fields of type $scopeType")
            }

            val elementType = scope.uniformFieldType
            if (elementType == CbFieldType.None) {
                allocate(1).put(type.value)
            } else if (elementType != type) {
                throw CbWriterException("Mismatched type for uniform array - expected $elementType, not $type")
            }

            scope.itemCount++
        } else {
            val scopeType = scope.fieldType
            if (!CbFieldUtils.isObject(scopeType.value)) {
                throw CbWriterException("Named fields are not allowed within fields of type $scopeType")
            }

            val elementType = scope.uniformFieldType

            val nameVarIntLength = VarULong.measureUnsigned(name.length.toLong())
            if (elementType == CbFieldType.None) {
                val buffer = allocate(SIZE_OF_CB_FIELD_TYPE + nameVarIntLength + name.length)
                buffer.put(type.value or CbFieldType.HasFieldName.value)
                writeBinaryPayload(buffer, name.toByteArray())
            } else {
                if (elementType != type) {
                    throw CbWriterException("Mismatched type for uniform object - expected $elementType, not $type")
                }
                val buffer = allocate(name.length)
                writeBinaryPayload(buffer, name.toByteArray())
            }

            scope.itemCount++
        }
    }

    override fun writeReference(data: ByteBuffer) {
        writeFields()
        addLeafData(data)
    }

    private fun writeBinaryPayload(output: ByteBuffer, value: ByteArray) {
        VarULong.writeUnsigned(output, value.size.toLong())
        output.put(value)
    }

    fun getSize(): Int {
        if (openScopes.count() > 1) {
            throw CbWriterException("Unfinished scope in writer")
        }

        var length = 0
        var child = rootScope.firstChild
        while (child != null) {
            length += child.length
            child = child.nextSibling
        }

        return length
    }

    fun copyTo(buffer: ByteBuffer) {
        copy(rootScope, buffer)
    }

    private fun copy(scope: Scope, buffer: ByteBuffer) {
        if (scope.dataSize() > 0) {
            buffer.put(scope.data)
        }
        var child = scope.firstChild
        while (child != null) {
            copy(child, buffer)
            child = child.nextSibling
        }
    }

    fun computeHash(): ByteArray {
        return BlakeUtils.hash(getSegments())
    }

    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(getSize())
        copyTo(buffer)
        return buffer.array()
    }

    fun getSegments(): List<ByteBuffer> {
        val segments = mutableListOf<ByteBuffer>()
        getSegments(rootScope, segments)
        return segments
    }

    private fun getSegments(scope: Scope, segments: MutableList<ByteBuffer>) {
        if (scope.dataSize() > 0) {
            segments.add(scope.data!!)
        }
        var child = scope.firstChild
        while (child != null) {
            getSegments(child, segments)
            child = child.nextSibling
        }
    }
}
