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

import java.nio.ByteBuffer

/**
 * CompactBinary的字段
 */
data class Scope(
    /**
     * 字段类型
     */
    var fieldType: CbFieldType = CbFieldType.None,

    /**
     * 序列化时是否写入字段类型
     * [CbFieldType.UniformArray]、[CbFieldType.UniformObject]统一设置了子字段的类型，子字段[writeFieldType]的值就是false
     */
    var writeFieldType: Boolean = true,

    /**
     * 子字段类型
     */
    var uniformFieldType: CbFieldType = CbFieldType.None,

    /**
     * 字段名
     */
    var name: String? = null,
    /**
     * 子字段数量
     */
    var itemCount: Int = 0,
    /**
     * 当前字段的数据
     * Array或Object存的是header（比如字段类型、字段名）的数据
     * 其他类型存的是实际数据
     */
    var data: ByteBuffer? = null,
    /**
     * [data]的长度加上所有子字段的[data]长度
     */
    var length: Int = 0,
    /**
     * 第一个子字段
     */
    var firstChild: Scope? = null,
    /**
     * 最后一个子字段
     */
    var lastChild: Scope? = null,
    /**
     * 子字段是链表结构，通过[nextSibling]可以找到相邻字段
     */
    var nextSibling: Scope? = null,
) {
    public fun reset()
    {
        fieldType = CbFieldType.None
        writeFieldType = true
        uniformFieldType = CbFieldType.None
        name = null
        itemCount = 0
        data = null
        length = 0
        firstChild = null
        lastChild = null
        nextSibling = null
    }

    fun addChild(child: Scope) {
        if (lastChild == null) {
            firstChild = child
        } else {
            lastChild!!.nextSibling = child
        }
        lastChild = child
    }

    /**
     * 获取所有子字段长度总和
     */
    fun childrenLength(): Int {
        var childrenLength = 0
        var child = firstChild
        while (child != null) {
            childrenLength += child.length
            child = child.nextSibling
        }
        return childrenLength
    }

    /**
     * 获取字段序列化后类型与字段名信息的长度
     */
    fun typeAndNameLength(): Int {
        var length = 0
        if (writeFieldType) {
            length++
            if (!name.isNullOrEmpty()) {
                length += VarULong.measureUnsigned(name!!.length.toLong()) + name!!.length
            }
        }
        return length
    }

    fun payloadTypeAndItemCountLength(): Int {
        var length = 0
        if (CbFieldUtils.isArray(fieldType.value)) {
            length += VarULong.measureUnsigned(itemCount.toLong())
        }
        if (fieldType.value == CbFieldType.UniformObject.value || fieldType.value == CbFieldType.UniformArray.value) {
            length++
        }
        return length
    }

    fun dataSize() = data?.remaining() ?: 0
}
